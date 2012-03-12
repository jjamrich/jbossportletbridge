package org.jboss.portletbridge.factory;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.util.Map;

import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.faces.FacesException;
import javax.faces.FacesWrapper;

import org.jboss.portletbridge.config.BridgeConfig;
import org.jboss.portletbridge.factory.BridgeFactory;
import org.jboss.portletbridge.logger.BridgeLogger;

public final class BridgeFactoryFinder
{

  private static Map<Class, List<String>> sFactoryDefinitions =
    new HashMap<Class, List<String>>(6);
  private static Map<Class, BridgeFactory<?>> sFactoryInstances =
    new HashMap<Class, BridgeFactory<?>>(6);
  private static ReentrantReadWriteLock sLock =
    new ReentrantReadWriteLock(true);


  public static void addFactoryDefinition(Class<? extends BridgeFactory> type,
                                          String factoryImplClassName)
  {
    sLock.writeLock().lock();
    try
    {
      List<String> defs = sFactoryDefinitions.get(type);
      if (defs == null)
      {
        defs = (List<String>) new ArrayList(4);
        sFactoryDefinitions.put(type, defs);
      }
      if (!defs.contains(factoryImplClassName))
      {
        defs.add(factoryImplClassName);
      }
    }
    finally
    {
      sLock.writeLock().unlock();
    }

  }

  public static List<String> getFactoryDefinition(Class<? extends BridgeFactory> type)
  {
    sLock.readLock().lock();
    try
    {
      return sFactoryDefinitions.get(type);
    }
    finally
    {
      sLock.readLock().unlock();
    }
  }

  public static void addFactoryInstance(Class<? extends BridgeFactory> type,
                                        BridgeFactory<?> factoryInstance)
  {
    sLock.writeLock().lock();
    try
    {
      sFactoryInstances.put(type, factoryInstance);
    }
    finally
    {
      sLock.writeLock().unlock();
    }
  }

  public static BridgeFactory<?> getFactoryInstance(Class<? extends BridgeFactory> type)
  {
    sLock.readLock().lock();
    try
    {
      BridgeFactory<?> instance = sFactoryInstances.get(type);
      if (instance != null)
      {
        return instance;
      }
    }
    finally
    {
      sLock.readLock().unlock();
    }

    // otherwise we need to instantiate it
    sLock.writeLock().lock();
    try
    {
      List<String> defs = getFactoryDefinition(type);

      if (defs == null)
      {
        //TODO: log something
        return null;
      }
      ClassLoader cl = Thread.currentThread().getContextClassLoader();

      BridgeFactory<?> factoryInstance = null;
      for (String s: defs)
      {
        factoryInstance =
            getImplGivenPreviousImpl(cl, s, type, factoryInstance);
      }
      
      // Now store instance in cache
      addFactoryInstance(type, factoryInstance);
      return factoryInstance;

    }
    finally
    {
      sLock.writeLock().unlock();
    }

  }

  private static BridgeFactory<?> getImplGivenPreviousImpl(ClassLoader classLoader,
                                                           String implName,
                                                           Class<? extends BridgeFactory> type,
                                                           Object previousImpl)
  {
    Class<? extends BridgeFactory> implClass = null;
    Class[] constructorArgs;
    Object[] newInstanceArgs = new Object[1];
    Constructor constructor;

    // if we have a previousImpl and the appropriate one arg ctor.
    if (previousImpl != null)
    {
      try
      {
        implClass = (Class<? extends BridgeFactory>) Class.forName(implName, false, classLoader);
        constructorArgs = new Class[1];
        constructorArgs[0] = type;
        constructor = implClass.getConstructor(constructorArgs);
        newInstanceArgs[0] = previousImpl;
        return (BridgeFactory<?>) constructor.newInstance(newInstanceArgs);
      }
      catch (NoSuchMethodException nsme)
      {
        // fall through to "zero-arg-ctor" case
        ;
      }
      catch (Exception e)
      {
        throw new FacesException(implName, e);
      }
    }

    // zero arg constructor
    try
    {
      if (implClass == null)
      {
        implClass = (Class<? extends BridgeFactory>) Class.forName(implName, false, classLoader);
      }
      // since this is the hard coded implementation default,
      // there is no preceding implementation, so don't bother
      // with a non-zero-arg ctor.
      return implClass.newInstance();
    }
    catch (Exception e)
    {
      throw new FacesException(implName, e);
    }
  }
}

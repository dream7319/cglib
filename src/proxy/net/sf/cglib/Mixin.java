/*
 * The Apache Software License, Version 1.1
 *
 * Copyright (c) 2002 The Apache Software Foundation.  All rights
 * reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by the
 *        Apache Software Foundation (http://www.apache.org/)."
 *    Alternately, this acknowledgment may appear in the software itself,
 *    if and wherever such third-party acknowledgments normally appear.
 *
 * 4. The names "Apache" and "Apache Software Foundation" must
 *    not be used to endorse or promote products derived from this
 *    software without prior written permission. For written
 *    permission, please contact apache@apache.org.
 *
 * 5. Products derived from this software may not be called "Apache",
 *    nor may "Apache" appear in their name, without prior written
 *    permission of the Apache Software Foundation.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE APACHE SOFTWARE FOUNDATION OR
 * ITS CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF
 * USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 */
package net.sf.cglib;

import java.lang.reflect.Constructor;
import java.util.*;
import net.sf.cglib.util.*;

/**
 * <code>Mixin</code> provides a number of static methods that allow
 * multiple objects to be combined into a single larger object. The
 * methods in the generated object simply call the original methods in the
 * underlying "delegate" objects.
 * @author Chris Nokleberg
 * @version $Id: Mixin.java,v 1.2 2003/09/09 20:59:59 herbyderby Exp $
 */
abstract public class Mixin {
    private static final FactoryCache CACHE = new FactoryCache(Mixin.class);
    private static final Map ROUTE_CACHE = Collections.synchronizedMap(new HashMap());
    private static final MixinKey KEY_FACTORY =
      (MixinKey)KeyFactory.create(MixinKey.class, null);

    interface MixinKey {
        public Object newInstance(Class[] interfaces, int[] route);
    }
    
    protected Mixin() { }

    abstract public Mixin newInstance(Object[] delegates);

    /**
     * Returns an object that implements all of the specified
     * interfaces. For each interface, all methods are delegated to the
     * respective object in the delegates argument array.
     * @param interfaces the array of interfaces to implement
     * @param delegates The array of delegates. Must be the same length
     * as the interface array, and each delegates must implements the
     * corresponding interface.
     * @param loader The ClassLoader to use. If null uses the one that
     * loaded this class.
     * @return the dynamically created object
     */
    public static Mixin create(Class[] interfaces, Object[] delegates, ClassLoader loader) {
        return createHelper(interfaces, delegates, null, loader);
    }

    /**
     * Returns an object that implements all of the specified
     * interfaces. For each interface, all methods are delegated to the
     * respective object in the delegates argument array.
     * @param interfaces the array of interfaces to implement
     * @param delegates The array of delegates. If the route parameter
     * is null, this must be the same length as the interface array. Each delegate
     * must implement the interfaces delegated to it.
     * @param route An optional routing table. Must be null, or the same length
     * as the interfaces array. If non null, the values represent which delegate
     * the corresponding interface should be mapped to. If null, a 1:1 correspondence
     * is assumed.
     * @param loader The ClassLoader to use. If null uses the one that
     * loaded this class.
     * @return the dynamically created object
     */
    public static Mixin create(Class[] interfaces,
                               Object[] delegates,
                               int[] route,
                               ClassLoader loader) {
        return createHelper(interfaces, delegates, route, loader);
    }
    

    /**
     * Returns an object that implements all of the interfaces
     * implemented by the specified objects. For each interface, all
     * methods are delegated to the first object in the argument array
     * which implements the interface.
     * <p>
     * <b>Note:</b> interfaces which have no methods (marker interfaces)
     * are not implemented by the returned object.
     * @param delegates the array of delegates
     * @param loader The ClassLoader to use. If null uses the one that
     * loaded this class.
     * @return the dynamically created object
     * @see #getInterfaceMap(Object[])
     */
    public static Mixin create(Object[] delegates, ClassLoader loader) {
        Route route = route(delegates);
        return createHelper(route.interfaces, delegates, route.route, loader);
    }

    /**
     * Combines an array of JavaBeans into a single "super" bean.
     * Calls to the super bean will delegate to the underlying beans.
     * @param beans the list of beans to delegate to
     * @param loader The ClassLoader to use. If null uses the one that loaded this class.
     */
    public static Object createBean(final Object[] beans, ClassLoader loader) {
        Object key = new ClassesKey(beans);
        return (Mixin)CACHE.get(loader, key, new FactoryCache.AbstractCallback() {
            public BasicCodeGenerator newGenerator() {
                return new MixinBeanGenerator(ReflectUtils.getClasses(beans));
            }
            public Object newInstance(Object factory, boolean isNew) {
                return ((Mixin)factory).newInstance(beans);
            }
        });
    }

    private static class Route
    {
        private Class[] interfaces;
        private int[] route;

        Route(Object[] delegates) {
            Map map = new HashMap();
            ArrayList collect = new ArrayList();
            for (int i = 0; i < delegates.length; i++) {
                Class delegate = delegates[i].getClass();
                collect.clear();
                collectAllInterfaces(delegate, collect);
                for (Iterator it = collect.iterator(); it.hasNext();) {
                    Class iface = (Class)it.next();
                    if (!map.containsKey(iface)) {
                        map.put(iface, new Integer(i));
                    }
                }
            }
            interfaces = new Class[map.size()];
            route = new int[map.size()];
            int index = 0;
            for (Iterator it = map.keySet().iterator(); it.hasNext();) {
                Class key = (Class)it.next();
                interfaces[index] = key;
                route[index] = ((Integer)map.get(key)).intValue();
                index++;
            }
        }
    }

    public static Class[] getInterfaces(Object[] delegates) {
        return (Class[])route(delegates).interfaces.clone();
    }

    public static int[] getRoute(Object[] delegates) {
        return (int[])route(delegates).route.clone();
    }
        
    private static Route route(Object[] delegates) {
        Object key = new ClassesKey(delegates);
        Route route = (Route)ROUTE_CACHE.get(key);
        if (route == null) {
            ROUTE_CACHE.put(key, route = new Route(delegates));
        }
        return route;
    }

    private static Mixin createHelper(final Class[] interfaces,
                                       final Object[] delegates,
                                       final int[] route,
                                       ClassLoader loader) {
        Object key = KEY_FACTORY.newInstance(interfaces, route);
        return (Mixin)CACHE.get(loader, key, new FactoryCache.AbstractCallback() {
            public BasicCodeGenerator newGenerator() {
                return new MixinGenerator(interfaces, route);
            }
            public Object newInstance(Object factory, boolean isNew) {
                return ((Mixin)factory).newInstance(delegates);
            }
        });
    }

    private static void collectAllInterfaces(Class type, List list) {
        if (!type.equals(Object.class)) {
            list.addAll(Arrays.asList(type.getInterfaces()));
            collectAllInterfaces(type.getSuperclass(), list);
        }
    }
}

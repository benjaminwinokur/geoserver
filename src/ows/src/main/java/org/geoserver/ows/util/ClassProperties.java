/* (c) 2014 Open Source Geospatial Foundation - all rights reserved
 * (c) 2001 - 2013 OpenPlans
 * This code is licensed under the GPL 2.0 license, available at the root
 * application directory.
 */
package org.geoserver.ows.util;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides lookup information about java bean properties in a class.
 *
 * @author Justin Deoliveira, OpenGEO
 * @author Andrea Aime, OpenGEO
 */
public class ClassProperties {
    private static final ListMultimap<String, Method> EMPTY =
            Multimaps.newListMultimap(ImmutableMap.of(), () -> null);

    private static final Set<String> COMMON_DERIVED_PROPERTIES =
            new HashSet<>(Arrays.asList("prefixedName"));
    private ListMultimap<String, Method> methods;
    private ListMultimap<String, Method> getters;
    private ListMultimap<String, Method> setters;

    public ClassProperties(Class<?> clazz) {
        methods = Multimaps.newListMultimap(new CaseInsensitiveMap<>(), ArrayList::new);
        getters = Multimaps.newListMultimap(new CaseInsensitiveMap<>(), ArrayList::new);
        setters = Multimaps.newListMultimap(new CaseInsensitiveMap<>(), ArrayList::new);
        for (Method method : clazz.getMethods()) {
            final String name = method.getName();
            methods.put(name, method);
            final Class<?>[] params = method.getParameterTypes();
            if ((name.startsWith("get")
                            || name.startsWith("is")
                            || COMMON_DERIVED_PROPERTIES.contains(name))
                    && params.length == 0) {
                getters.put(gp(method), method);
            } else if (name.startsWith("set") && params.length == 1) {
                setters.put(name.substring(3), method);
            }
        }

        // avoid keeping lots of useless empty arrays in memory for
        // the long term, use just one
        if (methods.isEmpty()) methods = EMPTY;
        if (getters.isEmpty()) getters = EMPTY;
        if (setters.isEmpty()) setters = EMPTY;
    }

    /**
     * Returns a list of all the properties of the class.
     *
     * @return A list of string.
     */
    public List<String> properties() {
        ArrayList<String> properties = new ArrayList<String>();
        for (String key : getters.keySet()) {
            if (key.equalsIgnoreCase("resource")) { // ??? WHY ???
                properties.add(0, key);
            } else {
                properties.add(key);
            }
        }
        return properties;
    }

    /**
     * Looks up a setter method by property name.
     *
     * <p>setter("foo",Integer) --&gt; void setFoo(Integer);
     *
     * @param property The property.
     * @param type The type of the property.
     * @return The setter for the property, or null if it does not exist.
     */
    public Method setter(String property, Class<?> type) {
        List<Method> methods = setters.get(property); // MultiMap never returns null
        for (int i = 0; i < methods.size(); i++) {
            Method setter = methods.get(i);
            if (type == null) {
                return setter;
            } else {
                Class<?> target = setter.getParameterTypes()[0];
                if (target.isAssignableFrom(type)
                        || (target.isPrimitive() && type == wrapper(target))
                        || (type.isPrimitive() && target == wrapper(type))) {
                    return setter;
                }
            }
        }

        // could not be found, try again with a more lax match
        String lax = lax(property);
        if (!lax.equals(property)) {
            return setter(lax, type);
        }

        return null;
    }

    /**
     * Looks up a getter method by its property name.
     *
     * <p>getter("foo",Integer) --&gt; Integer getFoo();
     *
     * @param property The property.
     * @param type The type of the property.
     * @return The getter for the property, or null if it does not exist.
     */
    public Method getter(String property, Class<?> type) {
        List<Method> methods = getters.get(property);
        if (!methods.isEmpty()) { // MultiMap never returns null
            if (type == null) {
                return methods.get(0);
            }
            for (int i = 0; i < methods.size(); i++) {
                Method getter = methods.get(i);
                Class<?> target = getter.getReturnType();
                if (type.isAssignableFrom(target)
                        || (target.isPrimitive() && type == wrapper(target))
                        || (type.isPrimitive() && target == wrapper(type))) {
                    return getter;
                }
            }
        }

        // could not be found, try again with a more lax match
        String lax = lax(property);
        if (!lax.equals(property)) {
            return getter(lax, type);
        }

        return null;
    }

    /**
     * Does some checks on the property name to turn it into a java bean property.
     *
     * <p>Checks include collapsing any "_" characters.
     */
    static String lax(String property) {
        return property.replaceAll("_", "");
    }

    /**
     * Returns the wrapper class for a primitive class.
     *
     * @param primitive A primtive class, like int.class, double.class, etc...
     */
    static Class<?> wrapper(Class<?> primitive) {
        if (boolean.class == primitive) {
            return Boolean.class;
        }
        if (char.class == primitive) {
            return Character.class;
        }
        if (byte.class == primitive) {
            return Byte.class;
        }
        if (short.class == primitive) {
            return Short.class;
        }
        if (int.class == primitive) {
            return Integer.class;
        }
        if (long.class == primitive) {
            return Long.class;
        }

        if (float.class == primitive) {
            return Float.class;
        }
        if (double.class == primitive) {
            return Double.class;
        }

        return null;
    }

    /** Looks up a method by name. */
    public Method method(String name) {
        List<Method> results = methods.get(name);
        if (results.isEmpty()) {
            return null;
        } else {
            return results.get(0);
        }
    }

    /** Returns the name of the property corresponding to the getter method. */
    String gp(Method getter) {
        String name = getter.getName();
        if (COMMON_DERIVED_PROPERTIES.contains(name)) {
            return name;
        }
        name = name.substring(name.startsWith("get") ? 3 : 2);
        return camelCase(name);
    }

    private String camelCase(String name) {
        char c1 = name.charAt(0);
        if (Character.isLowerCase(c1)) {
            return name;
        }
        return Character.toLowerCase(c1) + name.substring(1);
    }
}

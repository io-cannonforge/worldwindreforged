/*
 * Copyright 2025-2026 seaglassfoundry.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Part of the WorldWind Reforged project.
 *
 * New file — per-GLContext resource holder. Stores one value per OpenGL context so that
 * GPU resources (shaders, buffers, etc.) are not shared across contexts. Uses WeakHashMap
 * so entries are eligible for GC once a context is no longer referenced.
 */
package gov.nasa.worldwind.util;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;

import com.jogamp.opengl.GLContext;

/**
 * A per-{@link GLContext} value holder, analogous to {@link ThreadLocal} but keyed by the
 * current OpenGL context. Useful for GPU resources (shader programs, buffer objects) whose
 * IDs are valid only within the context that created them.
 * <p>
 * Backed by a synchronised {@link WeakHashMap} so that entries become eligible for garbage
 * collection once their owning context is no longer strongly referenced.
 *
 * @param <T> the type of value stored per context
 */
public class GLContextLocal<T>
{
    private final Map<GLContext, T> map = Collections.synchronizedMap(new WeakHashMap<>());

    /** Returns the value for the current GL context, or {@code null} if none has been set. */
    public T get()
    {
        GLContext ctx = GLContext.getCurrent();
        return ctx != null ? map.get(ctx) : null;
    }

    /** Returns the value for the given GL context, or {@code null} if none has been set. */
    public T get(GLContext ctx)
    {
        return ctx != null ? map.get(ctx) : null;
    }

    /** Sets the value for the current GL context. */
    public void set(T value)
    {
        GLContext ctx = GLContext.getCurrent();
        if (ctx != null)
            map.put(ctx, value);
    }

    /** Sets the value for the given GL context. */
    public void set(GLContext ctx, T value)
    {
        if (ctx != null)
            map.put(ctx, value);
    }

    /**
     * Returns the value for the current GL context, creating it with the supplied factory
     * if absent. The factory is called at most once per context.
     */
    public T computeIfAbsent(Supplier<T> factory)
    {
        GLContext ctx = GLContext.getCurrent();
        if (ctx == null)
            return null;
        synchronized (map)
        {
            T val = map.get(ctx);
            if (val == null)
            {
                val = factory.get();
                map.put(ctx, val);
            }
            return val;
        }
    }

    /** Removes the value for the current GL context. */
    public void remove()
    {
        GLContext ctx = GLContext.getCurrent();
        if (ctx != null)
            map.remove(ctx);
    }

    /** Returns {@code true} if a value is stored for the current GL context. */
    public boolean isSet()
    {
        GLContext ctx = GLContext.getCurrent();
        return ctx != null && map.containsKey(ctx);
    }
}

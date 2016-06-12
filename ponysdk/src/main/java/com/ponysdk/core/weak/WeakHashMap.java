/*
 * Copyright (c) 2011 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.core.weak;

import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.server.stm.Txn;
import com.ponysdk.core.ui.basic.PObject;
import com.ponysdk.core.ui.basic.PWindow;
import com.ponysdk.core.writer.ModelWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WeakHashMap implements Map<Integer, PObject> {

    private final Logger log = LoggerFactory.getLogger(WeakHashMap.class);

    private final ReferenceQueue<PObject> queue = new ReferenceQueue<>();

    private final Map<Integer, Reference<PObject>> referenceByObjectID = new ConcurrentHashMap<>();

    private final Map<WeakReference<PObject>, Integer> objectIDByReferences = new ConcurrentHashMap<>();

    @Override
    public int size() {
        expungeStaleEntries();
        return referenceByObjectID.size();
    }

    @Override
    public boolean isEmpty() {
        expungeStaleEntries();
        return referenceByObjectID.isEmpty();
    }

    @Override
    public boolean containsKey(final Object key) {
        expungeStaleEntries();
        return referenceByObjectID.containsKey(key);
    }

    @Override
    public boolean containsValue(final Object value) {
        expungeStaleEntries();
        return referenceByObjectID.containsValue(new WeakReference<>(value));
    }

    @Override
    public PObject get(final Object key) {
        expungeStaleEntries();
        final Reference<PObject> value = referenceByObjectID.get(key);
        if (value == null) return null;
        return value.get();
    }

    @Override
    public PObject put(final Integer objectID, final PObject value) {
        expungeStaleEntries();
        final WeakReference<PObject> weakReference = new WeakReference<>(value, queue);
        referenceByObjectID.put(objectID, weakReference);
        objectIDByReferences.put(weakReference, objectID);

        if (log.isDebugEnabled()) log.debug("Registering object: " + value);

        return value;
    }

    @Override
    public PObject remove(final Object key) {
        expungeStaleEntries();
        final Reference<PObject> reference = referenceByObjectID.remove(key);

        if (log.isDebugEnabled()) log.debug("Removing reference on object #" + key);
        if (reference == null) return null;

        objectIDByReferences.remove(reference);

        return reference.get();
    }

    @Override
    public void putAll(final Map<? extends Integer, ? extends PObject> m) {
        expungeStaleEntries();
    }

    @Override
    public void clear() {
        expungeStaleEntries();
    }

    @Override
    public Set<Integer> keySet() {
        expungeStaleEntries();
        return null;
    }

    @Override
    public Collection<PObject> values() {
        expungeStaleEntries();
        return null;
    }

    @Override
    public Set<java.util.Map.Entry<Integer, PObject>> entrySet() {
        expungeStaleEntries();
        return null;
    }

    private void expungeStaleEntries() {
        Reference<? extends PObject> reference;
        while ((reference = queue.poll()) != null) {
            final Integer objectID = objectIDByReferences.remove(reference);
            final Reference<PObject> removedObject = referenceByObjectID.remove(objectID);
            if (log.isDebugEnabled()) log.debug("Removing reference on object #{}", objectID);

            try (final ModelWriter writer = Txn.getWriter()) {
                final PObject pObject = removedObject.get();
                if (pObject == null) continue;
                final int windowID = pObject.getWindowID();
                if (windowID != PWindow.MAIN_WINDOW_ID) {
                    writer.writeModel(ServerToClientModel.WINDOW_ID, windowID);
                }
                writer.writeModel(ServerToClientModel.TYPE_GC, objectID);
            } catch (IOException e) {
            }
        }
    }

}
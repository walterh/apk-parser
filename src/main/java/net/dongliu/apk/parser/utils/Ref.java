package net.dongliu.apk.parser.utils;

import java.util.UUID;

import net.dongliu.apk.parser.exception.ArgumentException;

public class Ref<T> {

    private T value;

    public Ref(T value) {
        this.value = value;
    }

    public T get() {
        return value;
    }

    public void set(T val2) {
        value = val2;
    }

    @Override
    public String toString() {
        return value.toString();
    }

    @Override
    public boolean equals(Object obj) {
        return value.equals(obj);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    public Class<?> getInternalClass() {
        return value.getClass();
    }

    public static Ref<?>[] toRefs(Class<?>[] classes) {
        Ref<?>[] refs = new Ref<?>[classes.length];

        for (int i = 0; i < classes.length; i++) {
            if (classes[i] == UUID.class) {
                refs[i] = new Ref<UUID>(null);
            } else if (classes[i] == Long.class) {
                refs[i] = new Ref<Long>(null);
            } else if (classes[i] == Byte.class) {
                refs[i] = new Ref<Byte>(null);
            } else {
                throw new ArgumentException("unexpected class = " + Byte.class.toString());
            }
        }

        return refs;
    }

    public static Object[] toObjects(Ref<?>[] refs) {
        Object[] objs = new Object[refs.length];

        for (int i = 0; i < refs.length; i++) {
            objs[i] = refs[i].get();
        }

        return objs;
    }
}
package io.quarkiverse.machina.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Optional;

import io.quarkiverse.machina.Signal;
import io.quarkiverse.machina.SignalProducer;

public class SignalUtil {

    public static String toSignalName(Type type, Annotation[] annotations) {
        Signal signal = findSignal(annotations);
        if (signal != null) {
            return signal.value();
        }
        return toSignalName(type);
    }

    public static Signal findSignal(Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            if (annotation instanceof Signal) {
                return (Signal) annotation;
            }
        }
        return null;
    }

    public static String toSignalName(Type type) {
        Type signalType = signalType(type);
        if (signalType != null) {
            return asSignalName(signalType);
        }
        return asSignalName(type);
    }

    public static String asSignalName(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.equals(int.class)) {
                return "int";
            } else if (clazz.equals(double.class)) {
                return "double";
            } else if (clazz.equals(boolean.class)) {
                return "boolean";
            } else if (clazz.equals(String.class)) {
                return "String";
            } else if (clazz.equals(short.class)) {
                return "short";
            } else if (clazz.equals(long.class)) {
                return "long";
            } else if (clazz.equals(float.class)) {
                return "float";
            } else if (clazz.equals(byte.class)) {
                return "byte";
            } else if (clazz.equals(char.class)) {
                return "char";
            }
            return clazz.getName();
        } else {
            return type.toString();
        }
    }

    public static Type signalType(Type type) {
        Class<?> raw = toClass(type);
        if ((List.class.equals(raw) || SignalProducer.class.equals(raw))
                && type instanceof ParameterizedType pt) {
            return pt.getActualTypeArguments()[0];
        } else if (Optional.class.equals(raw)
                && type instanceof ParameterizedType pt) {
            Type optionalType = pt.getActualTypeArguments()[0];
            if (List.class.equals(toClass(optionalType)) && optionalType instanceof ParameterizedType pt2) {
                return pt2.getActualTypeArguments()[0];
            }
        }
        return null;
    }

    public static Class<?> toClass(Type type) {
        if (type instanceof Class<?>) {
            return (Class<?>) type;
        } else if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
        }
        return null;
    }
}

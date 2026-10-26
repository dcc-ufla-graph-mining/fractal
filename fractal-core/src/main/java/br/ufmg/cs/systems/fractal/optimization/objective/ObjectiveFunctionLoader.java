package br.ufmg.cs.systems.fractal.optimization.objective;

import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph;
import br.ufmg.cs.systems.fractal.optimization.VertexInducedOptimizationSubgraph.SerializableToDoubleFunction;
import br.ufmg.cs.systems.fractal.util.ReflectionSerializationUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

/**
 * Loads objective implementations supplied through the application classpath.
 *
 * <p>The objective JAR and its dependencies must be supplied to Spark.
 * This loader does not compile source files or modify the classpath.</p>
 */
public final class ObjectiveFunctionLoader {

    private ObjectiveFunctionLoader() {
        // This class provides only static loading operations.
    }

    /**
     * Creates an objective and checks that its initial state can be copied.
     *
     * <p>Implementations must be public, concrete classes with a public
     * no-argument constructor. They must implement
     * {@code SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>}.
     * Fields included in serialization must also be serializable.</p>
     *
     * @param className objective class name
     * @return the objective instance used by the application
     * @throws IllegalArgumentException if loading, construction, or copying fails
     */
    @SuppressWarnings("unchecked")
    public static SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>
    load(String className) {

        if (className == null || className.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "The objective class name must not be blank.");
        }

        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = ObjectiveFunctionLoader.class.getClassLoader();
        }

        try {
            // Spark exposes additional application JARs through this loader.
            Class<?> objectiveClass = Class.forName(className, false, loader);

            if (!SerializableToDoubleFunction.class.isAssignableFrom(objectiveClass)) {
                throw new IllegalArgumentException(
                        "Objective class '" + className + "' must implement "
                                + "SerializableToDoubleFunction"
                                + "<VertexInducedOptimizationSubgraph>.");
            }

            int modifiers = objectiveClass.getModifiers();
            if (!Modifier.isPublic(modifiers)
                    || Modifier.isAbstract(modifiers)
                    || objectiveClass.isInterface()) {
                throw new IllegalArgumentException(
                        "Objective class '" + className
                                + "' must be public and concrete.");
            }

            Object instance = objectiveClass.getConstructor().newInstance();

            SerializableToDoubleFunction<VertexInducedOptimizationSubgraph> objective =
                    (SerializableToDoubleFunction<VertexInducedOptimizationSubgraph>)
                            instance;

            // Detect invalid initial state before submitting optimization work.
            try {
                ReflectionSerializationUtils.clone(
                        objective, objectiveClass.getClassLoader());
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(
                        "Objective class '" + className
                                + "' could not be copied through serialization. "
                                + "Check its fields and serialization methods.",
                        e);
            }

            return objective;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "Objective class '" + className + "' was not found. "
                            + "Supply its JAR through Spark's additional JARs.",
                    e);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(
                    "Objective class '" + className
                            + "' requires a public no-argument constructor.",
                    e);
        } catch (InvocationTargetException e) {
            throw new IllegalArgumentException(
                    "The constructor of objective class '" + className
                            + "' failed.",
                    e.getCause());
        } catch (InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Objective class '" + className
                            + "' could not be instantiated.",
                    e);
        } catch (LinkageError e) {
            throw new IllegalArgumentException(
                    "Objective class '" + className
                            + "' could not be linked or initialized. "
                            + "Check its dependencies and Java version.",
                    e);
        } catch (SecurityException e) {
            throw new IllegalArgumentException(
                    "Access to objective class '" + className + "' was denied.",
                    e);
        }
    }
}
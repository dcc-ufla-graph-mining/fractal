package br.ufmg.cs.systems.fractal.util;

import br.ufmg.cs.systems.fractal.conf.SparkConfiguration;
import org.apache.log4j.Logger;

import java.io.*;

public class ReflectionSerializationUtils {
   private static final Logger LOG = Logger.getLogger(
           ReflectionSerializationUtils.class);

   public static <T> T newInstance(Class<T> clazz) {
      T object = null;
      try {
          object = clazz.newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
          throw new RuntimeException(e + " " + clazz);
      }
      return object;
   }

   public static <T extends Serializable> byte[] serialize(T obj) {
      try {
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         ObjectOutputStream oos = new ObjectOutputStream(baos);
         oos.writeObject(obj);
         return baos.toByteArray();
      } catch (IOException e) {
         throw new RuntimeException(e);
      }
   }

   public static <T extends Serializable> T deserialize(byte[] barray) {
      try {
         ByteArrayInputStream bais = new ByteArrayInputStream(barray);
         ObjectInputStream ois = new ObjectInputStream(bais);
         return (T) ois.readObject();
      } catch (IOException | ClassNotFoundException e) {
         throw new RuntimeException(e);
      }
   }

   public static <T extends Serializable> T clone(T obj) {
      return deserialize(serialize(obj));
   }

   /**
    * Copies an object using the supplied loader to resolve serialized classes.
    *
    * <p>Supports objective implementations and helper classes supplied
    * in external application JARs.</p>
    *
    * @param obj object to copy
    * @param classLoader loader that can resolve the object's implementation
    * @param <T> serializable object type
    * @return an independent deserialized copy
    * @throws IllegalArgumentException if the object is null
    * @throws RuntimeException if serialization or deserialization fails
    */
   @SuppressWarnings("unchecked")
   public static <T extends Serializable> T clone(
           T obj, final ClassLoader classLoader) {

      if (obj == null) {
         throw new IllegalArgumentException("The object to copy must not be null.");
      }

      byte[] bytes = serialize(obj);

      try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
           ObjectInputStream objects = new ObjectInputStream(input) {

              @Override
              protected Class<?> resolveClass(ObjectStreamClass descriptor)
                      throws IOException, ClassNotFoundException {

                 if (classLoader != null) {
                    try {
                       return Class.forName(
                               descriptor.getName(), false, classLoader);
                    } catch (ClassNotFoundException ignored) {
                       // Preserve standard handling, including primitives.
                    }
                 }

                 return super.resolveClass(descriptor);
              }
           }) {
         return (T) objects.readObject();
      } catch (IOException | ClassNotFoundException e) {
         throw new RuntimeException(
                 "Could not copy object of class '"
                         + obj.getClass().getName() + "'.",
                 e);
      }
   }
}

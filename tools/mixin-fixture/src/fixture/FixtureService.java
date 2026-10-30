package fixture;

import java.io.*;
import java.net.URL;
import java.util.*;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.platform.container.*;
import org.spongepowered.asm.mixin.transformer.*;
import org.spongepowered.asm.service.*;

/** Isolated bytecode host; never on the Minecraft or release classpath. */
public final class FixtureService extends MixinServiceAbstract
    implements IClassProvider, IClassBytecodeProvider {
  public static FixtureService instance;

  public FixtureService() {
    instance = this;
  }

  public String getName() {
    return "ContractBladeFixture";
  }

  public boolean isValid() {
    return true;
  }

  public IClassProvider getClassProvider() {
    return this;
  }

  public IClassBytecodeProvider getBytecodeProvider() {
    return this;
  }

  public ITransformerProvider getTransformerProvider() {
    return null;
  }

  public IClassTracker getClassTracker() {
    return null;
  }

  public IMixinAuditTrail getAuditTrail() {
    return null;
  }

  public Collection<String> getPlatformAgents() {
    return List.of();
  }

  public IContainerHandle getPrimaryContainer() {
    return new ContainerHandleVirtual("fixture");
  }

  public InputStream getResourceAsStream(String name) {
    return getClass().getClassLoader().getResourceAsStream(name);
  }

  public URL[] getClassPath() {
    return new URL[0];
  }

  public Class<?> findClass(String name) throws ClassNotFoundException {
    return findClass(name, false);
  }

  public Class<?> findClass(String name, boolean initialize) throws ClassNotFoundException {
    return Class.forName(name, initialize, getClass().getClassLoader());
  }

  public Class<?> findAgentClass(String name, boolean initialize) throws ClassNotFoundException {
    return findClass(name, initialize);
  }

  public ClassNode getClassNode(String name) throws ClassNotFoundException, IOException {
    return getClassNode(name, false);
  }

  public ClassNode getClassNode(String name, boolean runTransformers)
      throws ClassNotFoundException, IOException {
    try (var in = getResourceAsStream(name.replace('.', '/') + ".class")) {
      if (in == null) throw new ClassNotFoundException(name);
      var node = new ClassNode();
      new ClassReader(in).accept(node, 0);
      return node;
    }
  }

  public IMixinTransformer transformer() {
    return getInternal(IMixinTransformerFactory.class).createTransformer();
  }
}

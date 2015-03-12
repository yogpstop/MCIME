package com.yogpc.gi.asm;

import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import com.yogpc.gi.Analyzer;
import com.yogpc.gi.AsmFixer;
import com.yogpc.gi.Mapping;

public class Asm implements IClassTransformer {
  private static final void key(final MethodNode mn, final String cn) {
    String f1 = null, f2 = null;
    AbstractInsnNode a;
    final Iterator<AbstractInsnNode> i = mn.instructions.iterator();
    while (i.hasNext()) {
      a = i.next();
      if (a instanceof FieldInsnNode && "Z".equals(((FieldInsnNode) a).desc)
          && cn.equals(((FieldInsnNode) a).owner)) {
        if (((FieldInsnNode) a).name.equals(f1) || ((FieldInsnNode) a).name.equals(f2))
          continue;
        if (f1 == null)
          f1 = ((FieldInsnNode) a).name;
        else if (f2 == null)
          f2 = ((FieldInsnNode) a).name;
      }
    }
    mn.instructions.clear();
    final LabelNode l = new LabelNode();
    mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn, f1, "Z"));
    mn.instructions.add(new JumpInsnNode(Opcodes.IFNE, l));
    mn.instructions.add(new InsnNode(Opcodes.ICONST_0));
    mn.instructions.add(new InsnNode(Opcodes.IRETURN));
    mn.instructions.add(l);
    mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn, "manager",
        "Lcom/yogpc/gi/GuiTextFieldManager;"));
    mn.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
    mn.instructions.add(new FieldInsnNode(Opcodes.GETFIELD, cn, f2, "Z"));
    mn.instructions.add(new VarInsnNode(Opcodes.ILOAD, 1));
    mn.instructions.add(new VarInsnNode(Opcodes.ILOAD, 2));
    mn.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
        "com/yogpc/gi/GuiTextFieldManager", "hookTyped", "(ZCI)Z", false));
    mn.instructions.add(new InsnNode(Opcodes.IRETURN));
  }

  private static final void init(final MethodNode mn, final String cn) {
    AbstractInsnNode p = mn.instructions.getLast();
    while (p.getOpcode() != Opcodes.RETURN)
      p = p.getPrevious();
    p = p.getPrevious();
    mn.instructions.insert(p, new FieldInsnNode(Opcodes.PUTFIELD, cn, "manager",
        "Lcom/yogpc/gi/GuiTextFieldManager;"));
    mn.instructions.insert(p, new MethodInsnNode(Opcodes.INVOKESPECIAL,
        "com/yogpc/gi/GuiTextFieldManager", "<init>", "(L" + cn + ";)V", false));
    mn.instructions.insert(p, new VarInsnNode(Opcodes.ALOAD, 0));
    mn.instructions.insert(p, new InsnNode(Opcodes.DUP));
    mn.instructions.insert(p, new TypeInsnNode(Opcodes.NEW, "com/yogpc/gi/GuiTextFieldManager"));
    mn.instructions.insert(p, new VarInsnNode(Opcodes.ALOAD, 0));
  }

  private static final void draw(final MethodNode mn, final String cn, final String fn,
      final String frt, final String frf) {
    AbstractInsnNode p = mn.instructions.getFirst();
    while (p != null) {
      if (p.getOpcode() == Opcodes.RETURN) {
        mn.instructions.insertBefore(p, new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.insertBefore(p, new FieldInsnNode(Opcodes.GETFIELD, cn, "manager",
            "Lcom/yogpc/gi/GuiTextFieldManager;"));
        mn.instructions.insertBefore(p, new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.insertBefore(p, new FieldInsnNode(Opcodes.GETFIELD, cn, fn, "I"));
        mn.instructions.insertBefore(p, new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.insertBefore(p, new FieldInsnNode(Opcodes.GETFIELD, cn, frf, frt));
        mn.instructions.insertBefore(p, new MethodInsnNode(Opcodes.INVOKEVIRTUAL,
            "com/yogpc/gi/GuiTextFieldManager", "hookDraw", "(ILjava/lang/Object;)V", false));
      }
      p = p.getNext();
    }
  }

  private static final void focused(final MethodNode mn) {
    AbstractInsnNode ain = mn.instructions.getFirst();
    while (ain != null) {
      if (ain.getOpcode() == Opcodes.ICONST_0) {
        ain = mn.instructions.getFirst();
        mn.instructions.insertBefore(ain, new VarInsnNode(Opcodes.ALOAD, 0));
        mn.instructions.insertBefore(ain, new VarInsnNode(Opcodes.ILOAD, 1));
        mn.instructions.insertBefore(ain, new MethodInsnNode(Opcodes.INVOKESTATIC,
            "com/yogpc/gi/TFManager", "hookFocuse", "(Ljava/lang/Object;Z)V", false));
        return;
      }
      ain = ain.getNext();
    }
  }

  private static final void count(final MethodNode mn, final String cn,
      final Map<String, Integer> map) {
    AbstractInsnNode p = mn.instructions.getFirst();
    while (p != null) {
      if ((p.getOpcode() == Opcodes.GETFIELD || p.getOpcode() == Opcodes.PUTFIELD)
          && cn.equals(((FieldInsnNode) p).owner) && "I".equals(((FieldInsnNode) p).desc)) {
        final Integer i = map.get(((FieldInsnNode) p).name);
        final int j = i == null ? 1 : i.intValue() + 1;
        map.put(((FieldInsnNode) p).name, Integer.valueOf(j));
      }
      p = p.getNext();
    }
  }

  private static final void gtf(final ClassNode cn) {
    cn.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "manager",
        "Lcom/yogpc/gi/GuiTextFieldManager;", null, null));
    final Map<String, Integer> map = new HashMap<String, Integer>();
    for (final MethodNode mnode : cn.methods)
      if ("(I)V".equals(mnode.desc))
        count(mnode, cn.name, map);
    int maxV = 0;
    String maxK = null;
    for (final Map.Entry<String, Integer> e : map.entrySet())
      if (e.getValue().intValue() > maxV) {
        maxV = e.getValue().intValue();
        maxK = e.getKey();
      }
    String frf = null, frt = null;
    for (final MethodNode mn : cn.methods)
      if (mn.name.equals("<init>")) {
        final int shift = mn.desc.charAt(1) == 'L' ? 0 : 1;
        int phase = -1;
        AbstractInsnNode ain = mn.instructions.getFirst();
        while (ain != null) {
          if (phase < 0 && ain.getOpcode() == Opcodes.ALOAD && ((VarInsnNode) ain).var == 0)
            phase = 0;
          else if (phase == 0 && ain.getOpcode() == Opcodes.ALOAD
              && ((VarInsnNode) ain).var == 1 + shift)
            phase = 9999;
          else if (phase > 0 && ain.getOpcode() == Opcodes.PUTFIELD) {
            frf = ((FieldInsnNode) ain).name;
            frt = ((FieldInsnNode) ain).desc;
            phase = -1;
          } else
            phase = -1;
          ain = ain.getNext();
        }
      }
    for (final MethodNode mnode : cn.methods)
      if ("(CI)Z".equals(mnode.desc))
        key(mnode, cn.name);
      else if ("<init>".equals(mnode.name))
        init(mnode, cn.name);
      else if ("()V".equals(mnode.desc) && mnode.instructions.size() > 150)
        draw(mnode, cn.name, maxK, frt, frf);
      else if ("(Z)V".equals(mnode.desc))
        focused(mnode);
    for (final FieldNode fnode : cn.fields) {
      // FIXME all public
      fnode.access |= Opcodes.ACC_PUBLIC;
      fnode.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
    }
  }

  private static final void chat(final MethodNode mn, final String cn, final String fn,
      final String fd) {
    String fmn = null, fmo = null;
    AbstractInsnNode a;
    final Iterator<AbstractInsnNode> i = mn.instructions.iterator();
    while (i.hasNext()) {
      a = i.next();
      if (a instanceof MethodInsnNode && "(CI)Z".equals(((MethodInsnNode) a).desc)) {
        fmo = ((MethodInsnNode) a).owner;
        fmn = ((MethodInsnNode) a).name;
        break;
      }
    }
    a = mn.instructions.getFirst();
    mn.instructions.insertBefore(a, new VarInsnNode(Opcodes.ALOAD, 0));
    mn.instructions.insertBefore(a, new FieldInsnNode(Opcodes.GETFIELD, cn, fn, fd));
    mn.instructions.insertBefore(a, new VarInsnNode(Opcodes.ILOAD, 1));
    mn.instructions.insertBefore(a, new VarInsnNode(Opcodes.ILOAD, 2));
    mn.instructions.insertBefore(a, new MethodInsnNode(Opcodes.INVOKEVIRTUAL, fmo, fmn, "(CI)Z",
        false));
    final LabelNode l = new LabelNode();
    mn.instructions.insertBefore(a, new JumpInsnNode(Opcodes.IFEQ, l));
    mn.instructions.insertBefore(a, new InsnNode(Opcodes.RETURN));
    mn.instructions.insertBefore(a, l);
  }

  private static boolean done = false;

  private static final ClassNode gtfm(final ClassNode cn) {
    try {
      if (!done) {
        final URL[] urls = Launch.classLoader.getURLs();
        for (final URL url : urls) {
          boolean isTarget = false;
          ZipEntry ze;
          final InputStream is = url.openStream();
          final ZipInputStream in = new ZipInputStream(is);
          while ((ze = in.getNextEntry()) != null) {
            if (ze.getName().startsWith("META-INF/MOJANG")) {
              isTarget = true;
              break;
            }
            in.closeEntry();
          }
          in.close();
          is.close();
          if (isTarget)
            Analyzer.anis(url);
        }
        done = true;
      }
      final ClassNode out = new ClassNode();
      cn.accept(AsmFixer.InitAdapter(out, Mapping.I));
      return out;
    } catch (final Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static void mc(final ClassNode cn) {
    final List<String> map = new ArrayList<String>();
    for (final MethodNode mn : cn.methods)
      if ("(II)V".equals(mn.desc)) {
        AbstractInsnNode ain = mn.instructions.getFirst();
        while (ain != null) {
          if (ain instanceof FieldInsnNode && ((FieldInsnNode) ain).owner.equals(cn.name)
              && ((FieldInsnNode) ain).desc.startsWith("L"))
            map.add("(" + ((FieldInsnNode) ain).desc + ")V");
          ain = ain.getNext();
        }
      }
    for (final MethodNode mn : cn.methods)
      if (map.contains(mn.desc)) {
        final AbstractInsnNode a = mn.instructions.getFirst();
        mn.instructions.insertBefore(a, new VarInsnNode(Opcodes.ALOAD, 1));
        mn.instructions.insertBefore(a, new MethodInsnNode(Opcodes.INVOKESTATIC,
            "com/yogpc/gi/TFManager", "hookShowGui", "(Ljava/lang/Object;)V", false));
      }
  }

  private static boolean isMinecraft(final MethodNode mn) {
    AbstractInsnNode ain = mn.instructions.getFirst();
    while (ain != null) {
      if (ain instanceof LdcInsnNode
          && "########## GL ERROR ##########".equals(((LdcInsnNode) ain).cst))
        return true;
      ain = ain.getNext();
    }
    return false;
  }

  private static final void gc(final ClassNode cn) {
    String fn = null, fd = null;
    for (final FieldNode fnode : cn.fields)
      if (fnode.desc.startsWith("L") && fnode.desc.length() < 8) {
        fn = fnode.name;
        fd = fnode.desc;
      }
    for (final MethodNode mnode : cn.methods)
      if ("(CI)V".equals(mnode.desc))
        chat(mnode, cn.name, fn, fd);
  }

  private static final List<String> hwndmethods = new ArrayList<String>();
  static {
    hwndmethods.add("create(Lorg/lwjgl/opengl/PixelFormat;)V");
    hwndmethods.add("create()V");
    hwndmethods.add("setDisplayMode(Lorg/lwjgl/opengl/DisplayMode;)V");
    hwndmethods.add("setFullscreen(Z)V");
  }

  @Override
  public byte[] transform(final String name, final String transformedName, final byte[] ba) {
    ClassNode cn = new ClassNode();
    final ClassReader cr = new ClassReader(ba);
    boolean modified = false;
    cr.accept(cn, ClassReader.EXPAND_FRAMES);
    for (final MethodNode mn : cn.methods) {
      AbstractInsnNode ain = mn.instructions.getFirst();
      while (ain != null) {
        fs: if (ain instanceof MethodInsnNode) {
          final MethodInsnNode min = (MethodInsnNode) ain;
          if (!"org/lwjgl/opengl/Display".equals(min.owner))
            break fs;
          if (!hwndmethods.contains(min.name + min.desc))
            break fs;
          mn.instructions.insert(ain, new MethodInsnNode(Opcodes.INVOKESTATIC,
              "com/yogpc/gi/TFManager", "updateWnd", "()V", false));
          modified = true;
        }
        ain = ain.getNext();
      }
    }
    if ("com.yogpc.gi.GuiTextFieldManager".equals(name) || "com.yogpc.gi.TFManager".equals(name)) {
      cn = gtfm(cn);
      modified = true;
    }
    if (name.length() < 4)// TODO Obfuscated detection
      for (final MethodNode mn : cn.methods)
        if ("(IIZ)I".equals(mn.desc)) {
          gtf(cn);
          modified = true;
        } else if ("([Ljava/lang/String;)V".equals(mn.desc)) {
          gc(cn);
          modified = true;
        } else if (isMinecraft(mn)) {
          mc(cn);
          modified = true;
        }
    if (!modified)
      return ba;
    final ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
    cn.accept(cw);
    return cw.toByteArray();
  }
}

package nz.temple.trailer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.nio.charset.StandardCharsets;
import java.security.ProtectionDomain;

public final class ClockAgent {
	private static final String CLOCK = "nz/temple/trailer/VirtualClock";
	private static final byte[][] NEEDLES = {bytes("nanoTime"), bytes("currentTimeMillis"), bytes("glfwGetTime")};
	private ClockAgent() {
	}
	public static void premain(String args, Instrumentation instrumentation) {
		instrumentation.addTransformer(new Transformer(), false);
		System.out.println("TEMPLE_TRAILER clock agent installed");
	}
	private static boolean wanted(String name) {
		if (name.startsWith("nz/temple/smoke/") || name.startsWith("nz/temple/trailer/")) {
			return false;
		}
		return name.startsWith("net/minecraft/") || name.startsWith("com/mojang/") || name.startsWith("nz/temple/");
	}
	private static final class Transformer implements ClassFileTransformer {
		@Override
		public byte[] transform(ClassLoader loader, String name, Class<?> redefining, ProtectionDomain domain, byte[] bytes) {
			if (name == null || !wanted(name) || !mentionsClock(bytes)) {
				return null;
			}
			try {
				ClassReader reader = new ClassReader(bytes);
				ClassWriter writer = new ClassWriter(reader, 0);
				boolean[] changed = {false};
				reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
					@Override
					public MethodVisitor visitMethod(int access, String method, String desc, String signature, String[] exceptions) {
						return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, method, desc, signature, exceptions)) {
							@Override
							public void visitMethodInsn(int opcode, String owner, String callee, String calleeDesc, boolean itf) {
								if (opcode == Opcodes.INVOKESTATIC && owner.equals("java/lang/System")
										&& (callee.equals("nanoTime") || callee.equals("currentTimeMillis"))) {
									changed[0] = true;
									super.visitMethodInsn(opcode, CLOCK, callee, calleeDesc, false);
									return;
								}
								super.visitMethodInsn(opcode, owner, callee, calleeDesc, itf);
								if (opcode == Opcodes.INVOKESTATIC && owner.equals("org/lwjgl/glfw/GLFW") && callee.equals("glfwGetTime")) {
									changed[0] = true;
									super.visitMethodInsn(Opcodes.INVOKESTATIC, CLOCK, "adjustSeconds", "(D)D", false);
								}
							}
						};
					}
				}, 0);
				return changed[0] ? writer.toByteArray() : null;
			} catch (Throwable t) {
				System.err.println("TEMPLE_TRAILER could not retime " + name + ": " + t);
				return null;
			}
		}
	}
	private static boolean mentionsClock(byte[] bytes) {
		for (byte[] needle : NEEDLES) {
			if (indexOf(bytes, needle) >= 0) {
				return true;
			}
		}
		return false;
	}
	private static int indexOf(byte[] hay, byte[] needle) {
		outer:
		for (int i = 0, end = hay.length - needle.length; i <= end; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (hay[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}
	private static byte[] bytes(String s) {
		return s.getBytes(StandardCharsets.UTF_8);
	}
}

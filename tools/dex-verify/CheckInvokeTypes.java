import org.jf.dexlib2.Opcode;
import org.jf.dexlib2.Opcodes;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.MethodImplementation;
import org.jf.dexlib2.iface.instruction.FiveRegisterInstruction;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.ReferenceInstruction;
import org.jf.dexlib2.iface.instruction.RegisterRangeInstruction;
import org.jf.dexlib2.iface.instruction.TwoRegisterInstruction;
import org.jf.dexlib2.iface.reference.MethodReference;
import org.jf.dexlib2.iface.reference.TypeReference;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Tracks reference types from the dex itself and checks invoke arguments. */
public final class CheckInvokeTypes {
    public static void main(String[] args) throws Exception {
        DexBackedDexFile dex = new DexBackedDexFile(
                Opcodes.forApi(30), Files.readAllBytes(Path.of(args[0])));
        String only = args.length > 1 ? args[1] : null;
        int failed = 0;
        int checked = 0;
        for (ClassDef type : dex.getClasses()) {
            for (Method method : type.getMethods()) {
                MethodImplementation impl = method.getImplementation();
                if (impl == null) {
                    continue;
                }
                String name = type.getType() + "->" + method.getName();
                if (only != null && !name.contains(only)) {
                    continue;
                }
                checked++;
                Map<Integer, String> regs = new HashMap<>();
                String pendingResult = null;
                int address = 0;
                for (Instruction instruction : impl.getInstructions()) {
                    Opcode opcode = instruction.getOpcode();
                    if (pendingResult != null && opcode.name.startsWith("move-result")) {
                        if (instruction instanceof OneRegisterInstruction) {
                            regs.put(((OneRegisterInstruction) instruction).getRegisterA(), pendingResult);
                        }
                        pendingResult = null;
                    } else if (pendingResult != null) {
                        pendingResult = null;
                    }
                    if (opcode.setsResult() && instruction instanceof ReferenceInstruction) {
                        Object ref = ((ReferenceInstruction) instruction).getReference();
                        if (ref instanceof MethodReference) {
                            pendingResult = ((MethodReference) ref).getReturnType();
                        }
                    }
                    if (opcode == Opcode.CONST_STRING || opcode == Opcode.CONST_STRING_JUMBO) {
                        regs.put(((OneRegisterInstruction) instruction).getRegisterA(), "Ljava/lang/String;");
                    } else if (opcode == Opcode.NEW_INSTANCE) {
                        Object ref = ((ReferenceInstruction) instruction).getReference();
                        regs.put(((OneRegisterInstruction) instruction).getRegisterA(),
                                ((TypeReference) ref).getType());
                    } else if (opcode == Opcode.MOVE_OBJECT || opcode == Opcode.MOVE_OBJECT_FROM16
                            || opcode == Opcode.MOVE_OBJECT_16) {
                        TwoRegisterInstruction move = (TwoRegisterInstruction) instruction;
                        regs.put(move.getRegisterA(), regs.get(move.getRegisterB()));
                    } else if (opcode == Opcode.CHECK_CAST) {
                        Object ref = ((ReferenceInstruction) instruction).getReference();
                        regs.put(((OneRegisterInstruction) instruction).getRegisterA(),
                                ((TypeReference) ref).getType());
                    }
                    if (opcode.name.startsWith("invoke-")) {
                        MethodReference called = (MethodReference) ((ReferenceInstruction) instruction).getReference();
                        List<? extends CharSequence> params = called.getParameterTypes();
                        List<Integer> used = argumentRegisters(instruction, opcode);
                        int start = 0;
                        if (!opcode.name.contains("-static")) {
                            start = 1;
                        }
                        for (int i = start; i < used.size() && i - start < params.size(); i++) {
                            String actual = regs.get(used.get(i));
                            String expected = params.get(i - start).toString();
                            if (actual != null && !actual.equals(expected)
                                    && !isArrayOf(actual, expected)) {
                                failed++;
                                System.out.println("FAIL " + name + " +" + Integer.toHexString(address)
                                        + " v" + used.get(i) + " has " + actual + " expected " + expected
                                        + " calling " + called.getDefiningClass() + "->" + called.getName());
                            }
                        }
                    }
                    address += instruction.getCodeUnits() * 2;
                }
            }
        }
        System.out.println("checked=" + checked + " failed=" + failed);
        if (failed != 0) {
            System.exit(2);
        }
    }

    private static boolean isArrayOf(String actual, String expected) {
        return false;
    }

    private static List<Integer> argumentRegisters(Instruction instruction, Opcode opcode) {
        List<Integer> used = new ArrayList<>();
        if (instruction instanceof FiveRegisterInstruction) {
            FiveRegisterInstruction five = (FiveRegisterInstruction) instruction;
            int count = five.getRegisterCount();
            if (count > 0) used.add(five.getRegisterC());
            if (count > 1) used.add(five.getRegisterD());
            if (count > 2) used.add(five.getRegisterE());
            if (count > 3) used.add(five.getRegisterF());
            if (count > 4) used.add(five.getRegisterG());
        } else if (instruction instanceof RegisterRangeInstruction) {
            RegisterRangeInstruction range = (RegisterRangeInstruction) instruction;
            for (int i = 0; i < range.getRegisterCount(); i++) {
                used.add(range.getStartRegister() + i);
            }
        }
        return used;
    }
}

package cn.muyang.instructions;

import cn.muyang.MethodContext;
import org.objectweb.asm.tree.LabelNode;

public class LabelHandler extends GenericInstructionHandler<LabelNode> {

    @Override
    public void accept(MethodContext context, LabelNode node) {
        context.method.tryCatchBlocks.stream().filter(x -> x.start.equals(node))
                .forEachOrdered(context.tryCatches::add);
        context.method.tryCatchBlocks.stream().filter(x -> x.end.equals(node))
                .forEachOrdered(context.tryCatches::remove);
        try {
            super.accept(context, node);
        } catch (UnsupportedOperationException ex) {
            // ignored
        }

       /* String m = "//LabelHandler:" + node.getLabel() + context.getLabelPool().getName(node.getLabel()) + " tryCatch:";
        for (TryCatchBlockNode tryCatch : context.tryCatches) {
            m = m + ":" + tryCatch.type;
        }
        m = m + "\n";*/
        context.output.append(String.format("%s:;\n", context.getLabelPool().getName(node.getLabel())));
        //context.output.append(String.format("%s: ;\n", context.getLabelPool().getName(node.getLabel())));
    }

    @Override
    public String insnToString(MethodContext context, LabelNode node) {
        return String.format("LABEL %s", context.getLabelPool().getName(node.getLabel()));
    }

    @Override
    public int getNewStackPointer(LabelNode node, int currentStackPointer) {
        return currentStackPointer;
    }

    @Override
    protected void process(MethodContext context, LabelNode node) {
        throw new UnsupportedOperationException("break at super.process()");
    }
}

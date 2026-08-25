package com.github.cooldood.utils.client;

import com.github.cooldood.events.Bus;
import com.github.cooldood.events.impl.FileDroppedEvent;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.util.List;

public class FrameUtil {
    // stupid   G89HoergguiogghoHUG,
    // this probably has 99 problems and they are NOT all bitches. <- kid cudi reference, not jay z
    // but i want to drag and drop files.
    public static void createCookiesFrame() {
        JFrame frame = new JFrame("Drop Cookies Files Here");
        frame.setSize(400, 400);
        frame.setLocationRelativeTo(null);

        frame.setDropTarget(new DropTarget() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>) dtde.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    Bus.post(new FileDroppedEvent(droppedFiles));
                    dtde.dropComplete(true);
                } catch (Exception e) {
                    System.err.println("Failed to handle file drop: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });

        frame.setVisible(true);
    }
}

package org.ak.billing.commands;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/** Komutları çalıştırır, geri alma (undo) ve yineleme (redo) geçmişini tutar. */
public class CommandInvoker {
    private final Deque<Command> undoStack = new ArrayDeque<>();
    private final Deque<Command> redoStack = new ArrayDeque<>();

    public boolean executeCommand(Command command) {
        boolean succeeded = command.execute();
        if (succeeded) {
            undoStack.push(command);
            redoStack.clear();
        }
        return succeeded;
    }

    public Optional<Command> undoLastCommand() {
        Command command = undoStack.poll();
        if (command != null) {
            command.undo();
            redoStack.push(command);
        }
        return Optional.ofNullable(command);
    }

    public Optional<Command> redoLastCommand() {
        Command command = redoStack.poll();
        if (command != null && command.execute()) {
            undoStack.push(command);
            return Optional.of(command);
        }
        return Optional.empty();
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }
}

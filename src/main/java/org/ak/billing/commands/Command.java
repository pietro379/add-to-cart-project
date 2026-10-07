package org.ak.billing.commands;

public interface Command {
    /** İşlemi uygular; başarısızsa {@code false} döner ve geçmişe eklenmez. */
    boolean execute();

    /** {@link #execute()} öncesindeki duruma döner. */
    void undo();

    /** Kullanıcıya gösterilecek kısa açıklama, ör. "2 x MAC AIR PRO ekleme". */
    String describe();
}

package org.Azkerash.create_bioships.client.components;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.Azkerash.create_bioships.components.logic.nerve.*;
import org.Azkerash.create_bioships.core.access.SubLevelPermissions.Entry;

/** Management UI only: all identity and authority checks are repeated on the server. */
public final class NervePanelScreen extends Screen {
  private NervePanelPayload data;
  private int tab, page, x, y;
  private String search = "";
  private final List<Button> rows = new ArrayList<>();
  private Button previous, next, refresh;

  public NervePanelScreen(NervePanelPayload data) {
    super(Component.literal("Nerve Panel"));
    this.data = data;
  }

  public static void receive(NervePanelPayload data) {
    var mc = Minecraft.getInstance();
    if (mc.screen instanceof NervePanelScreen screen
        && screen.data.grid().equals(data.grid())
        && screen.data.panel().equals(data.panel())) {
      screen.data = data;
      screen.rebuildRows();
    } else mc.setScreen(new NervePanelScreen(data));
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }

  @Override
  protected void init() {
    x = (width - 360) / 2;
    y = (height - 258) / 2;
    rows.clear();
    String[] tabs = {"Allowed", "Players", "Named mobs"};
    for (int i = 0; i < 3; i++) {
      final int value = i;
      addRenderableWidget(
          Button.builder(
                  Component.literal(tabs[i]),
                  b -> {
                    tab = value;
                    page = 0;
                    rebuildRows();
                  })
              .bounds(x + 12 + i * 113, y + 52, 109, 18)
              .build());
    }
    var filter = new EditBox(font, x + 14, y + 78, 332, 18, Component.literal("Filter names"));
    filter.setMaxLength(80);
    filter.setHint(Component.literal("Filter by name…"));
    filter.setValue(search);
    filter.setResponder(
        value -> {
          search = value;
          page = 0;
          rebuildRows();
        });
    addRenderableWidget(filter);
    previous =
        addRenderableWidget(
            Button.builder(
                    Component.literal("<"),
                    b -> {
                      page--;
                      rebuildRows();
                    })
                .bounds(x + 12, y + 232, 24, 18)
                .build());
    next =
        addRenderableWidget(
            Button.builder(
                    Component.literal(">"),
                    b -> {
                      page++;
                      rebuildRows();
                    })
                .bounds(x + 40, y + 232, 24, 18)
                .build());
    refresh =
        addRenderableWidget(
            Button.builder(Component.literal("Refresh"), b -> send(0, new UUID(0, 0)))
                .bounds(x + 190, y + 232, 74, 18)
                .build());
    addRenderableWidget(
        Button.builder(Component.literal("Done"), b -> onClose())
            .bounds(x + 274, y + 232, 74, 18)
            .build());
    rebuildRows();
  }

  private List<Entry> entries() {
    var source = tab == 0 ? data.allowed() : data.candidates();
    return source.stream()
        .filter(e -> tab == 0 || e.player() == (tab == 1))
        .filter(e -> e.name().toLowerCase(Locale.ROOT).contains(search.toLowerCase(Locale.ROOT)))
        .sorted(
            Comparator.comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::id))
        .toList();
  }

  private void rebuildRows() {
    if (previous == null) return;
    for (var button : rows) removeWidget(button);
    rows.clear();
    var entries = entries();
    page = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / 7));
    for (int i = 0; i < 7 && page * 7 + i < entries.size(); i++) {
      var entry = entries.get(page * 7 + i);
      int rowY = y + 104 + i * 17;
      String label = font.plainSubstrByWidth(entry.name(), 208);
      var info =
          Button.builder(Component.literal(label), b -> {})
              .bounds(x + 12, rowY, 216, 16)
              .tooltip(
                  Tooltip.create(
                      Component.literal(
                          (entry.player() ? "Player: " : "Named mob: ")
                              + entry.name()
                              + "\n"
                              + entry.id())))
              .build();
      addRenderableWidget(info);
      rows.add(info);
      boolean allowed = data.allowed().stream().anyMatch(e -> e.id().equals(entry.id()));
      var action =
          Button.builder(
                  Component.literal(tab == 0 ? "Remove" : allowed ? "Added" : "Add"),
                  b -> send(tab == 0 ? 2 : 1, entry.id()))
              .bounds(x + 232, rowY, 48, 16)
              .build();
      action.active = data.editable() && (tab == 0 || !allowed);
      addRenderableWidget(action);
      rows.add(action);
      if (tab == 1) {
        var transfer =
            Button.builder(Component.literal("Owner"), b -> confirmTransfer(entry))
                .bounds(x + 284, rowY, 64, 16)
                .build();
        transfer.active = data.editable();
        addRenderableWidget(transfer);
        rows.add(transfer);
      }
    }
    previous.active = page > 0;
    next.active = (page + 1) * 7 < entries.size();
    refresh.active = data.editable();
  }

  private void confirmTransfer(Entry entry) {
    minecraft.setScreen(
        new ConfirmScreen(
            yes -> {
              minecraft.setScreen(this);
              if (yes) send(3, entry.id());
            },
            Component.literal("Transfer ownership to " + entry.name() + "?"),
            Component.literal("You will lose owner access. Existing guests will keep access.")));
  }

  private void send(int action, UUID target) {
    PacketDistributor.sendToServer(new NervePanelAction(data.grid(), data.panel(), action, target));
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    renderBackground(g, mouseX, mouseY, partial);
    OrganScreenStyle.panel(g, x, y, 360, 258);
    g.drawCenteredString(font, title, x + 180, y + 10, OrganScreenStyle.TEXT);
    g.drawString(
        font,
        "Owner: " + font.plainSubstrByWidth(data.owner(), 250),
        x + 14,
        y + 29,
        OrganScreenStyle.TEXT,
        false);
    g.drawString(
        font,
        data.editable()
            ? "Membrane permissions · named mobs within 32 blocks"
            : "Read-only · only the owner can manage access",
        x + 14,
        y + 40,
        OrganScreenStyle.MUTED,
        false);
    if (entries().isEmpty())
      g.drawCenteredString(font, "No matching entries", x + 180, y + 147, OrganScreenStyle.MUTED);
    g.drawString(
        font,
        "Page " + (page + 1) + " / " + Math.max(1, (entries().size() + 6) / 7),
        x + 74,
        y + 237,
        OrganScreenStyle.MUTED,
        false);
    super.render(g, mouseX, mouseY, partial);
  }
}

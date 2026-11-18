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
          new OrganButton(
                  x + 12 + i * 113,
                  y + 52,
                  109,
                  18,
                  tabs[i],
                  b -> {
                    tab = value;
                    page = 0;
                    rebuildRows();
                  })
              .selected(() -> tab == value)
              .accent(0xffc9aa76));
    }
    var filter = new EditBox(font, x + 32, y + 82, 308, 14, Component.literal("Filter names"));
    filter.setBordered(false);
    filter.setTextColor(OrganScreenStyle.TEXT);
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
            new OrganButton(
                x + 12,
                y + 232,
                24,
                18,
                "<",
                b -> {
                  page--;
                  rebuildRows();
                }));
    next =
        addRenderableWidget(
            new OrganButton(
                x + 40,
                y + 232,
                24,
                18,
                ">",
                b -> {
                  page++;
                  rebuildRows();
                }));
    refresh =
        addRenderableWidget(
            new OrganButton(x + 190, y + 232, 74, 18, "Refresh", b -> send(0, new UUID(0, 0))));
    addRenderableWidget(
        new OrganButton(x + 274, y + 232, 74, 18, "Done", b -> onClose()).accent(0xffa8bc8b));
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
    page = Math.clamp(page, 0, Math.max(0, (entries.size() - 1) / 5));
    for (int i = 0; i < 5 && page * 5 + i < entries.size(); i++) {
      var entry = entries.get(page * 5 + i);
      int rowY = y + 103 + i * 25;
      boolean allowed = data.allowed().stream().anyMatch(e -> e.id().equals(entry.id()));
      var info = new AccessCard(x + 12, rowY, entry, allowed);
      info.setTooltip(
          Tooltip.create(
              Component.literal(
                  (entry.player() ? "Player: " : "Named mob: ")
                      + entry.name()
                      + "\n"
                      + entry.id())));
      addRenderableWidget(info);
      rows.add(info);
      var action =
          new OrganButton(
                  x + 232,
                  rowY + 2,
                  48,
                  20,
                  tab == 0 ? "Remove" : allowed ? "Added" : "Add",
                  b -> send(tab == 0 ? 2 : 1, entry.id()))
              .accent(tab == 0 ? 0xffcb8583 : 0xffb5c690);
      action.active = data.editable() && (tab == 0 || !allowed);
      addRenderableWidget(action);
      rows.add(action);
      if (tab == 1) {
        var transfer =
            new OrganButton(x + 284, rowY + 2, 64, 20, "Owner", b -> confirmTransfer(entry))
                .accent(0xffd5bb83);
        transfer.active = data.editable();
        addRenderableWidget(transfer);
        rows.add(transfer);
      }
    }
    previous.active = page > 0;
    next.active = (page + 1) * 5 < entries.size();
    refresh.active = data.editable();
  }

  private void confirmTransfer(Entry entry) {
    minecraft.setScreen(new OwnershipConfirmation(entry));
  }

  private final class OwnershipConfirmation extends Screen {
    private final Entry target;

    OwnershipConfirmation(Entry target) {
      super(Component.literal("Transfer ownership"));
      this.target = target;
    }

    @Override
    public boolean isPauseScreen() {
      return false;
    }

    @Override
    protected void init() {
      int left = (width - 304) / 2, top = (height - 146) / 2;
      addRenderableWidget(new OrganButton(left + 16, top + 112, 126, 20, "Cancel", b -> onClose()));
      addRenderableWidget(
          new OrganButton(
                  left + 162,
                  top + 112,
                  126,
                  20,
                  "Transfer",
                  b -> {
                    minecraft.setScreen(NervePanelScreen.this);
                    send(3, target.id());
                  })
              .accent(0xffd5bb83));
    }

    @Override
    public void onClose() {
      minecraft.setScreen(NervePanelScreen.this);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
      renderBackground(g, mx, my, partial);
      int left = (width - 304) / 2, top = (height - 146) / 2;
      OrganScreenStyle.panel(g, left, top, 304, 146);
      g.drawCenteredString(font, title, left + 152, top + 11, OrganScreenStyle.TEXT);
      g.drawCenteredString(font, "New owner: " + target.name(), left + 152, top + 42, 0xffe8cd97);
      g.drawCenteredString(
          font, "You will lose owner access.", left + 152, top + 65, OrganScreenStyle.TEXT);
      g.drawCenteredString(
          font, "Existing guests will keep access.", left + 152, top + 81, OrganScreenStyle.MUTED);
      for (var widget : renderables) widget.render(g, mx, my, partial);
    }
  }

  private static final class AccessCard extends Button {
    private final Entry entry;
    private final boolean allowed;

    AccessCard(int x, int y, Entry entry, boolean allowed) {
      super(x, y, 216, 24, Component.literal(entry.name()), b -> {}, DEFAULT_NARRATION);
      this.entry = entry;
      this.allowed = allowed;
    }

    @Override
    protected void renderWidget(GuiGraphics g, int mx, int my, float partial) {
      int x = getX(), y = getY();
      var font = Minecraft.getInstance().font;
      inset(g, x, y, 216, 24, isHoveredOrFocused() ? 0xff926078 : 0xff563044);
      g.fill(x + 2, y + 3, x + 4, y + 21, allowed ? 0xffa1ba88 : 0xffb18b71);
      g.renderItem(
          (entry.player()
                  ? net.minecraft.world.item.Items.PLAYER_HEAD
                  : net.minecraft.world.item.Items.NAME_TAG)
              .getDefaultInstance(),
          x + 8,
          y + 4);
      g.drawString(
          font,
          font.plainSubstrByWidth(entry.name(), 180),
          x + 30,
          y + 3,
          OrganScreenStyle.TEXT,
          false);
      g.drawString(
          font,
          (entry.player() ? "Player" : "Named mob")
              + " · "
              + (allowed ? "Access granted" : "Not authorized"),
          x + 30,
          y + 13,
          allowed ? 0xffafc699 : OrganScreenStyle.MUTED,
          false);
    }
  }

  private static void inset(GuiGraphics g, int x, int y, int w, int h, int edge) {
    g.fill(x, y, x + w, y + h, edge);
    g.fillGradient(x + 1, y + 1, x + w - 1, y + h - 1, 0xff24141f, 0xff160f19);
    g.fill(x + 2, y + h - 2, x + w - 2, y + h - 1, 0xff422438);
  }

  private void send(int action, UUID target) {
    PacketDistributor.sendToServer(new NervePanelAction(data.grid(), data.panel(), action, target));
  }

  @Override
  public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
    renderBackground(g, mouseX, mouseY, partial);
    OrganScreenStyle.panel(g, x, y, 360, 258);
    g.fillGradient(x + 8, y + 6, x + 352, y + 44, 0xff482334, 0xff25141f);
    g.renderItem(
        org.Azkerash.create_bioships
            .components
            .ComponentRegistry
            .NERVE_PANEL
            .get()
            .asItem()
            .getDefaultInstance(),
        x + 16,
        y + 13);
    g.drawString(font, title, x + 42, y + 11, OrganScreenStyle.TEXT, false);
    String description =
        !data.editable()
            ? "Read only · owner access required"
            : tab == 0
                ? "Authorized membrane guests"
                : tab == 1 ? "Online players" : "Nearby named guests · 32 blocks";
    g.drawString(font, description, x + 42, y + 27, OrganScreenStyle.MUTED, false);
    inset(g, x + 246, y + 9, 100, 31, 0xff876549);
    int pulse = data.editable() ? 0xffb7c68c : 0xffbc8298;
    g.fill(x + 253, y + 16, x + 258, y + 21, pulse);
    g.drawString(font, "OWNER", x + 264, y + 14, 0xffc9b185, false);
    g.drawString(
        font,
        font.plainSubstrByWidth(data.owner(), 86),
        x + 253,
        y + 26,
        OrganScreenStyle.TEXT,
        false);
    OrganScreenStyle.divider(g, x + 12, y + 47, 336);
    inset(g, x + 12, y + 77, 336, 20, 0xff7d4861);
    g.fill(x + 20, y + 83, x + 25, y + 84, 0xffb78b9d);
    g.fill(x + 19, y + 84, x + 20, y + 88, 0xffb78b9d);
    g.fill(x + 25, y + 84, x + 26, y + 88, 0xffb78b9d);
    g.fill(x + 20, y + 88, x + 25, y + 89, 0xffb78b9d);
    g.fill(x + 25, y + 89, x + 28, y + 91, 0xffb78b9d);
    inset(g, x + 10, y + 100, 340, 129, 0xff5d344b);
    if (entries().isEmpty())
      g.drawCenteredString(font, "No matching entries", x + 180, y + 147, OrganScreenStyle.MUTED);
    g.drawString(
        font,
        "Page " + (page + 1) + " / " + Math.max(1, (entries().size() + 4) / 5),
        x + 74,
        y + 237,
        OrganScreenStyle.MUTED,
        false);
    for (var widget : renderables) widget.render(g, mouseX, mouseY, partial);
  }
}

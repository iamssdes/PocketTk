package apple.rt;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** ttk.Treeview: columns, headings, nested rows that open and close, selection, tags. Drawn as a header row and a scrolling list of rows. */
final class Tree {
    static final Object NOPE = new Object();

    static final class Node {
        String id;
        Node parent;
        String text = "";
        List<Object> values = new ArrayList<Object>();
        boolean open;
        boolean detached;
        List<String> tags = new ArrayList<String>();
        final List<Node> kids = new ArrayList<Node>();
    }

    static final class Col {
        String id, heading = "";
        int width = 100, minwidth = 20;
        String anchor = "w";
        Object command;
    }

    final Ui.Wid w;
    final LinearLayout view, head;
    final LinearLayout rowsBox;
    final ScrollView scroll;
    final Node root = new Node();
    final Map<String, Node> byId = new LinkedHashMap<String, Node>();
    final List<Col> cols = new ArrayList<Col>();
    final Col tree = new Col();                      // the "#0" column
    final List<String> selection = new ArrayList<String>();
    final Map<String, Map<String, Object>> tagCfg = new LinkedHashMap<String, Map<String, Object>>();
    String focus;
    String show = "tree headings";
    String selectmode = "extended";
    int rowsShown = 6;
    int autoId = 1;
    Object selFn, openFn, closeFn, dblFn;
    long lastTapAt;
    String lastTap;

    Tree(Ui.Wid w, Context a) {
        this.w = w;
        root.id = "";
        root.open = true;
        tree.id = "#0";
        tree.width = 200;
        view = new LinearLayout(a);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackgroundColor(0xFFFFFFFF);
        head = new LinearLayout(a);
        head.setOrientation(LinearLayout.HORIZONTAL);
        view.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        scroll = new ScrollView(a);
        rowsBox = new LinearLayout(a);
        rowsBox.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rowsBox, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        applyHeight();
    }

    static Py.PyError err(String m) { return new Py.PyError("TclError", m); }

    // ---------------------------------------------------------------- options (called for Treeview(...) and configure)

    void option(String k, Object v) {
        switch (k) {
            case "columns": {
                cols.clear();
                for (Object c : listOf(v)) { Col c2 = new Col(); c2.id = Py.str(c); cols.add(c2); }
                rebuild();
                break;
            }
            case "displaycolumns": break;
            case "show": show = " " + String.join(" ", listStrs(v)) + " "; show = show.trim(); rebuild(); break;
            case "selectmode": selectmode = Py.str(v); break;
            case "height": rowsShown = Math.max(1, (int) Py.dbl(v)); applyHeight(); break;
            default: break;
        }
    }

    void applyHeight() {
        scroll.setMinimumHeight(Ui.dp(36) * Math.min(rowsShown, 12));
    }

    static List<Object> listOf(Object v) {
        List<Object> r = new ArrayList<Object>();
        if (v instanceof List) r.addAll((List<?>) v);
        else if (v != null) {
            String s = Py.str(v).trim();
            if (!s.isEmpty()) for (String x : s.split("\\s+")) r.add(x);
        }
        return r;
    }

    static List<String> listStrs(Object v) {
        List<String> r = new ArrayList<String>();
        for (Object o : listOf(v)) r.add(Py.str(o));
        return r;
    }

    boolean showTree() { return show.contains("tree"); }
    boolean showHead() { return show.contains("headings"); }

    // ---------------------------------------------------------------- lookups

    Col col(Object id) {
        String s = Py.str(id);
        if (s.equals("#0")) return tree;
        for (int i = 0; i < cols.size(); i++) {
            if (cols.get(i).id.equals(s)) return cols.get(i);
            if (s.equals("#" + (i + 1))) return cols.get(i);
        }
        throw err("Invalid column index " + s);
    }

    int colIndex(Object id) {
        String s = Py.str(id);
        for (int i = 0; i < cols.size(); i++) if (cols.get(i).id.equals(s) || s.equals("#" + (i + 1))) return i;
        throw err("Invalid column index " + s);
    }

    Node node(Object id) {
        String s = Py.str(id);
        if (s.isEmpty()) return root;
        Node n = byId.get(s);
        if (n == null) throw err("Item " + s + " not found");
        return n;
    }

    boolean isUnder(Node n, Node anc) {
        for (Node p = n; p != null; p = p.parent) if (p == anc) return true;
        return false;
    }

    // ---------------------------------------------------------------- drawing

    static int weightOf(Col c) { return Math.max(10, c.width); }

    static int gravityFor(String an) {
        if (an.equals("center")) return android.view.Gravity.CENTER_HORIZONTAL;
        if (an.contains("e")) return android.view.Gravity.END;
        return android.view.Gravity.START;
    }

    TextView cell(String text, Col c, int bg, int fg, boolean bold) {
        TextView t = new TextView(Ui.ctx);
        t.setText(text);
        t.setSingleLine(true);
        t.setTextColor(fg);
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14f);
        if (bold) t.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        t.setGravity(gravityFor(c.anchor));
        t.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(8));
        if (bg != 0) t.setBackgroundColor(bg);
        return t;
    }

    void rebuild() {
        head.removeAllViews();
        head.setVisibility(showHead() ? View.VISIBLE : View.GONE);
        if (showHead()) {
            List<Col> shown = shownCols();
            for (final Col c : shown) {
                TextView t = cell(c.heading.isEmpty() && c != tree ? "" : c.heading, c, 0xFFE0E0E0, 0xFF000000, true);
                if (c.command != null) {
                    final Object fn = c.command;
                    t.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { Ui.call(fn); } });
                }
                head.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weightOf(c)));
            }
        }
        rowsBox.removeAllViews();
        addRows(root, 0);
        view.requestLayout();
    }

    List<Col> shownCols() {
        List<Col> r = new ArrayList<Col>();
        if (showTree()) r.add(tree);
        r.addAll(cols);
        return r;
    }

    void addRows(Node parent, int depth) {
        for (final Node n : parent.kids) {
            if (n.detached) continue;
            boolean sel = selection.contains(n.id);
            int bg = sel ? 0xFFB3D9FF : 0;
            int fg = 0xFF000000;
            for (String tg : n.tags) {
                Map<String, Object> cfg = tagCfg.get(tg);
                if (cfg == null) continue;
                Integer b = cfg.get("background") == null ? null : Ui.color(cfg.get("background"));
                Integer f = cfg.get("foreground") == null ? null : Ui.color(cfg.get("foreground"));
                if (b != null && !sel) bg = b;
                if (f != null) fg = f;
            }
            LinearLayout row = new LinearLayout(Ui.ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (bg != 0) row.setBackgroundColor(bg);
            if (showTree()) {
                String mark = n.kids.isEmpty() ? "   " : (n.open ? "▾ " : "▸ ");
                StringBuilder ind = new StringBuilder();
                for (int i = 0; i < depth; i++) ind.append("    ");
                TextView t = cell(ind + mark + n.text, tree, 0, fg, false);
                final Node nn = n;
                if (!n.kids.isEmpty()) {
                    // tapping the arrow area toggles; the rest of the row selects
                }
                row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weightOf(tree)));
            }
            for (int i = 0; i < cols.size(); i++) {
                String v = i < n.values.size() ? Py.str(n.values.get(i)) : "";
                row.addView(cell(v, cols.get(i), 0, fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weightOf(cols.get(i))));
            }
            row.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { tap(n); } });
            rowsBox.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (n.open) addRows(n, depth + 1);
        }
    }

    boolean dirty;

    /** Many inserts in a row redraw once, after the script's turn. */
    void later() {
        if (dirty) return;
        dirty = true;
        Ui.postUi(new Runnable() { public void run() { dirty = false; rebuild(); } });
    }

    // ---------------------------------------------------------------- user taps

    void tap(Node n) {
        long now = System.currentTimeMillis();
        boolean dbl = n.id.equals(lastTap) && now - lastTapAt < 450;
        lastTap = n.id;
        lastTapAt = now;
        if (!selectmode.equals("none")) {
            boolean multi = selectmode.equals("extended");
            if (multi && selection.contains(n.id)) selection.remove(n.id);
            else { if (!multi) selection.clear(); selection.add(n.id); }
        }
        focus = n.id;
        if (!n.kids.isEmpty() && (dbl || !showHeadingsOnly())) {
            // a tap on a parent row selects it; a double tap (or a tap when the tree column is shown) opens or closes it
            if (dbl || showTree()) toggle(n);
        }
        rebuild();
        fire(selFn);
        if (dbl && dblFn != null) fire(dblFn);
    }

    boolean showHeadingsOnly() { return !showTree(); }

    void toggle(Node n) {
        n.open = !n.open;
        fire(n.open ? openFn : closeFn);
    }

    void fire(Object fn) {
        if (fn != null) Ui.call(fn, new Ext.Event(w));
    }

    void bind(String seq, Object fn) {
        if (seq.contains("TreeviewSelect")) selFn = fn;
        else if (seq.contains("TreeviewOpen")) openFn = fn;
        else if (seq.contains("TreeviewClose")) closeFn = fn;
        else dblFn = fn;                                    // <Double-1>, <Double-Button-1>, <Button-1>, <Return>
    }

    // ---------------------------------------------------------------- methods

    static Object tuple(List<?> l) { return new Py.Tuple(l); }

    List<Node> items(List<Object> pos) {
        List<Node> r = new ArrayList<Node>();
        for (Object o : pos) {
            if (o instanceof List) for (Object x : (List<?>) o) r.add(node(x));
            else r.add(node(o));
        }
        return r;
    }

    Object itemDict(Node n) {
        List<Object> vals = new ArrayList<Object>(n.values);
        List<Object> tg = new ArrayList<Object>(n.tags);
        return Py.dict("text", n.text, "image", "", "values", tuple(vals), "open", n.open ? 1L : 0L, "tags", tuple(tg));
    }

    void applyItem(Node n, Map<String, Object> kw) {
        for (Map.Entry<String, Object> e : kw.entrySet()) {
            switch (e.getKey()) {
                case "text": n.text = Py.str(e.getValue()); break;
                case "values": n.values = new ArrayList<Object>(listOf(e.getValue())); break;
                case "open": n.open = Ui.flag(e.getValue()); break;
                case "tags": n.tags = listStrs(e.getValue()); break;
                default: break;
            }
        }
    }

    void remove(Node n) {
        for (Node k : new ArrayList<Node>(n.kids)) remove(k);
        if (n.parent != null) n.parent.kids.remove(n);
        byId.remove(n.id);
        selection.remove(n.id);
        if (n.id.equals(focus)) focus = null;
    }

    Object call(String name, List<Object> pos, Map<String, Object> kw) {
        Object a0 = pos.size() > 0 ? pos.get(0) : null;
        Object a1 = pos.size() > 1 ? pos.get(1) : null;
        Object r = call0(name, pos, kw, a0, a1);
        if (r != NOPE) later();
        return r;
    }

    Object call0(String name, List<Object> pos, Map<String, Object> kw, Object a0, Object a1) {
        switch (name) {
            case "heading": {
                Col c = col(a0);
                if (pos.size() == 2 && kw.isEmpty()) { String o = Py.str(a1); return o.equals("text") ? c.heading : o.equals("anchor") ? c.anchor : ""; }
                for (Map.Entry<String, Object> e : kw.entrySet()) {
                    switch (e.getKey()) {
                        case "text": c.heading = Py.str(e.getValue()); break;
                        case "anchor": c.anchor = Py.str(e.getValue()); break;
                        case "command": c.command = e.getValue(); break;
                        default: break;
                    }
                }
                return null;
            }
            case "column": {
                Col c = col(a0);
                if (kw.isEmpty()) return Py.dict("id", c.id, "width", (long) c.width, "minwidth", (long) c.minwidth, "anchor", c.anchor, "stretch", 1L);
                for (Map.Entry<String, Object> e : kw.entrySet()) {
                    switch (e.getKey()) {
                        case "width": c.width = (int) Py.dbl(e.getValue()); break;
                        case "minwidth": c.minwidth = (int) Py.dbl(e.getValue()); break;
                        case "anchor": c.anchor = Py.str(e.getValue()); break;
                        default: break;
                    }
                }
                return null;
            }
            case "insert": {
                Node p = node(a0 == null ? "" : a0);
                Node n = new Node();
                String iid = kw.get("iid") == null ? null : Py.str(kw.get("iid"));
                if (iid == null || iid.isEmpty()) { do { iid = "I" + String.format("%03X", autoId++); } while (byId.containsKey(iid)); }
                else if (byId.containsKey(iid)) throw err("Item " + iid + " already exists");
                n.id = iid;
                n.parent = p;
                applyItem(n, kw);
                int idx = p.kids.size();
                if (a1 instanceof Number) idx = Math.max(0, Math.min(p.kids.size(), (int) ((Number) a1).longValue()));
                p.kids.add(idx, n);
                byId.put(iid, n);
                return iid;
            }
            case "delete": for (Node n : items(pos)) remove(n); return null;
            case "detach": for (Node n : items(pos)) n.detached = true; return null;
            case "move": case "reattach": {
                Node n = node(a0), np = node(a1);
                if (isUnder(np, n)) throw err("Cannot move an item beneath itself");
                if (n.parent != null) n.parent.kids.remove(n);
                n.parent = np;
                n.detached = false;
                Object ix = pos.size() > 2 ? pos.get(2) : "end";
                int idx = ix instanceof Number ? Math.max(0, Math.min(np.kids.size(), (int) ((Number) ix).longValue())) : np.kids.size();
                np.kids.add(idx, n);
                return null;
            }
            case "item": {
                Node n = node(a0);
                if (kw.isEmpty() && pos.size() == 2) {
                    String o = Py.str(a1);
                    Object d = itemDict(n);
                    return ((Map<?, ?>) d).get(o);
                }
                if (kw.isEmpty()) return itemDict(n);
                applyItem(n, kw);
                return null;
            }
            case "set": {
                Node n = node(a0);
                if (pos.size() == 1) {
                    Map<Object, Object> d = new LinkedHashMap<Object, Object>();
                    for (int i = 0; i < cols.size(); i++) d.put(cols.get(i).id, i < n.values.size() ? n.values.get(i) : "");
                    return d;
                }
                int ci = colIndex(a1);
                if (pos.size() == 2) return ci < n.values.size() ? n.values.get(ci) : "";
                while (n.values.size() <= ci) n.values.add("");
                n.values.set(ci, pos.get(2));
                return null;
            }
            case "get_children": {
                List<Object> r = new ArrayList<Object>();
                for (Node k : node(a0 == null ? "" : a0).kids) r.add(k.id);
                return tuple(r);
            }
            case "set_children": {
                Node p = node(a0);
                for (Node k : new ArrayList<Node>(p.kids)) { p.kids.remove(k); k.parent = null; }
                for (int i = 1; i < pos.size(); i++) { Node n = node(pos.get(i)); if (n.parent != null) n.parent.kids.remove(n); n.parent = p; p.kids.add(n); }
                return null;
            }
            case "exists": return Boolean.valueOf(byId.containsKey(Py.str(a0)));
            case "parent": { Node n = node(a0); return n.parent == null || n.parent == root ? "" : n.parent.id; }
            case "index": { Node n = node(a0); return (long) (n.parent == null ? 0 : n.parent.kids.indexOf(n)); }
            case "next": case "prev": {
                Node n = node(a0);
                if (n.parent == null) return "";
                int i = n.parent.kids.indexOf(n) + (name.equals("next") ? 1 : -1);
                return i >= 0 && i < n.parent.kids.size() ? n.parent.kids.get(i).id : "";
            }
            case "selection": {
                List<Object> r = new ArrayList<Object>(selection);
                return tuple(r);
            }
            case "selection_set": case "selection_add": case "selection_remove": case "selection_toggle": {
                List<Node> its = items(pos);
                if (name.equals("selection_set")) selection.clear();
                for (Node n : its) {
                    boolean has = selection.contains(n.id);
                    if (name.equals("selection_remove") || (name.equals("selection_toggle") && has)) selection.remove(n.id);
                    else if (!has) selection.add(n.id);
                }
                if (!its.isEmpty()) focus = its.get(0).id;
                fire(selFn);
                return null;
            }
            case "focus": {
                if (pos.isEmpty()) return focus == null ? "" : focus;
                focus = node(a0).id;
                return null;
            }
            case "see": {
                for (Node p = node(a0).parent; p != null; p = p.parent) p.open = true;
                return null;
            }
            case "tag_configure": {
                Map<String, Object> m = tagCfg.get(Py.str(a0));
                if (m == null) { m = new LinkedHashMap<String, Object>(); tagCfg.put(Py.str(a0), m); }
                m.putAll(kw);
                return null;
            }
            case "tag_has": {
                List<Object> r = new ArrayList<Object>();
                String tg = Py.str(a0);
                for (Node n : byId.values()) if (n.tags.contains(tg)) r.add(n.id);
                return tuple(r);
            }
            case "identify_row": case "identify": case "identify_column": case "identify_region": case "yview": case "xview": case "yview_moveto": case "bbox": return "";
            default: return NOPE;
        }
    }
}

package android.view;

public class Display {
    private final int id;
    public Display(int id) { this.id = id; }
    public int getDisplayId() { return id; }
    public String getName() { return "Fake display " + id; }
}

package colliderrun;

public class BoardTile {
    public final int row;
    public final int col;
    public TileType type;
    public boolean visited;

    public BoardTile(int row, int col, TileType type) {
        this.row = row;
        this.col = col;
        this.type = type;
    }
}

package dev.airpodsnative.companion;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

/** Exposes one bundled illustration, never device identity or arbitrary files.
 * This does not register Bluetooth metadata or grant privileged permissions.
 */
public final class DeviceArtworkProvider extends ContentProvider {
    public static final String PATH = "/airpods-max.png";
    private File artwork;
    @Override public boolean onCreate() {
        try { ensureArtwork(); ArtworkUriAccess.grantToSettings(getContext()); return true; }
        catch (FileNotFoundException e) { return false; }
    }
    /** Cache eviction can happen while this process lives. Publish only complete PNGs. */
    private synchronized void ensureArtwork() throws FileNotFoundException {
        android.content.SharedPreferences prefs = getContext().getSharedPreferences("selected_airpods", 0);
        String bond = prefs.getString("bond_address", null);
        AirPodsFinish finish = AirPodsFinish.Companion.fromSaved(
                bond == null ? null : prefs.getString("color." + bond, null));
        artwork = new File(getContext().getCacheDir(), "system-airpods-max-v4-" + finish.name() + ".png");
        if (artwork.isFile() && artwork.length() > 0) return;
        File temporary = null;
        Bitmap bitmap = null;
        try {
            temporary = File.createTempFile("system-airpods-max-", ".png", getContext().getCacheDir());
            bitmap = renderArtwork(finish.getDrawable());
            try (FileOutputStream out = new FileOutputStream(temporary)) {
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out))
                    throw new java.io.IOException("PNG encoding failed");
            }
            if (!temporary.renameTo(artwork)) throw new java.io.IOException("PNG publication failed");
        } catch (Exception e) {
            throw new FileNotFoundException("Artwork unavailable");
        } finally {
            if (bitmap != null) bitmap.recycle();
            if (temporary != null) temporary.delete();
        }
    }
    /** Preserve the bundled drawable's real aspect ratio, remove transparent padding,
     * and fit its visible pixels inside Settings' circular icon without clipping. */
    private Bitmap renderArtwork(int drawableId) throws java.io.IOException {
        Drawable drawable = getContext().getDrawable(drawableId);
        int intrinsicWidth = drawable.getIntrinsicWidth();
        int intrinsicHeight = drawable.getIntrinsicHeight();
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0)
            throw new java.io.IOException("Artwork has no intrinsic dimensions");
        float rasterScale = Math.min(1f, 1024f / Math.max(intrinsicWidth, intrinsicHeight));
        int width = Math.max(1, Math.round(intrinsicWidth * rasterScale));
        int height = Math.max(1, Math.round(intrinsicHeight * rasterScale));
        Bitmap source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        try {
            drawable.setBounds(0, 0, width, height);
            drawable.draw(new Canvas(source));
            int[] pixels = new int[width * height];
            source.getPixels(pixels, 0, width, 0, 0, width, height);
            int left = width, top = height, right = -1, bottom = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((pixels[y * width + x] >>> 24) == 0) continue;
                    left = Math.min(left, x); right = Math.max(right, x);
                    top = Math.min(top, y); bottom = Math.max(bottom, y);
                }
            }
            if (right < left) throw new java.io.IOException("Artwork is transparent");
            float centerX = (left + right + 1) / 2f;
            float centerY = (top + bottom + 1) / 2f;
            double radius = 0;
            for (int y = top; y <= bottom; y++) {
                for (int x = left; x <= right; x++) {
                    if ((pixels[y * width + x] >>> 24) == 0) continue;
                    // Include the whole pixel, not only its center.
                    double dx = Math.abs(x + 0.5 - centerX) + 0.5;
                    double dy = Math.abs(y + 0.5 - centerY) + 0.5;
                    radius = Math.max(radius, Math.hypot(dx, dy));
                }
            }
            float scale = (float) (124 / radius); // Four pixels for resampling/edge clearance.
            Bitmap result = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);
            canvas.translate(128 - centerX * scale, 128 - centerY * scale);
            canvas.scale(scale, scale);
            canvas.drawBitmap(source, 0, 0, new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG));
            return result;
        } finally { source.recycle(); }
    }
    private void requireArtwork(Uri uri) {
        if (!"content".equals(uri.getScheme()) ||
            !(getContext().getPackageName() + ".artwork").equals(uri.getAuthority()) ||
            !PATH.equals(uri.getEncodedPath()) || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Unknown artwork URI");
    }
    @Override public String getType(Uri uri) { requireArtwork(uri); return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        requireArtwork(uri);
        if (!"r".equals(mode)) throw new FileNotFoundException("Artwork is read-only");
        ensureArtwork();
        return ParcelFileDescriptor.open(artwork, ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] selectionArgs, String sortOrder) {
        requireArtwork(uri);
        try { ensureArtwork(); }
        catch (FileNotFoundException e) { throw new IllegalStateException("Artwork unavailable", e); }
        String[] columns = projection == null ? new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE} : projection;
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        Object[] row = new Object[columns.length];
        for (int i = 0; i < columns.length; i++) {
            if (OpenableColumns.DISPLAY_NAME.equals(columns[i])) row[i] = "airpods-max.png";
            else if (OpenableColumns.SIZE.equals(columns[i])) row[i] = artwork.length();
        }
        cursor.addRow(row); return cursor;
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException("Read-only"); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException("Read-only"); }
}

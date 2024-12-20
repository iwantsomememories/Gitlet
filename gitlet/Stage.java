package gitlet;

import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Stage implements Serializable {
    public HashMap<String, String> index;

    public static final String REMOVAL = "removal";

    public Stage() {
        index = new HashMap<>();
    }

    /** Save file f in the staging area. If file f does not exist, return null. Otherwise, return its SHA-1 value. */
    public void trackFile(File f) {
        if (!f.exists()) {
            return;
        }
        String fileName = f.getName();
        byte[] contents = Utils.readContents(f);
        String id = Utils.sha1(contents, fileName);

        if (!index.containsKey(fileName) || !index.get(fileName).equals(id)) {
            Utils.saveBlob(contents, id);
            index.put(fileName, id);
        }
    }

    /** Write the contents of the stage area to commit. */
    public void finalCommit(Commit c) {
        for (Map.Entry<String, String> e : index.entrySet()) {
            String key = e.getKey();
            String value = e.getValue();
            if (value.equals(Stage.REMOVAL)) {
                c.blobs.remove(key);
            } else {
                c.blobs.put(key, value);
            }
        }

        index.clear();
        writeStage();
    }

    public void writeStage() {
        Utils.writeObject(Repository.STAGE_AREA, this);
    }

    public static String getId(File f) {
        if (!f.exists()) {
            return null;
        }
        byte[] contents = Utils.readContents(f);
        return Utils.sha1(contents, f.getName());
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("Stage:\n");
        for (Map.Entry<String, String> e : this.index.entrySet()) {
            ret.append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
        }
        return ret.toString();
    }
}

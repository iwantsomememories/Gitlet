package gitlet;

import java.io.Serializable;
import java.util.*;

/** Represents a gitlet commit object.
 *  Including commit object generation, uid determination, output format, etc.
 *
 *  @author fqcd
 */
public class Commit implements Serializable {
    /** The message of this Commit. */
    private String message;

    /** The date of this Commit. */
    private String date;

    /** The id of the Commit. */
    public String id;

    /** The id of the first parent of Commit. */
    public String parent1 = null;

    /** The id of the second parent of Commit. */
    public String parent2 = null;

    /** Map of the filename to the uid. */
    public TreeMap<String, String> blobs;

    public Commit(String message) {
        this.message = message;
        Date now = new Date();
        Formatter formatter = new Formatter();
        formatter.format("Date: %ta %<tb %<td %<tT %<tY %<tz", now);
        this.date = formatter.toString();
        formatter.close();
        this.blobs = new TreeMap<>();
    }

    public Commit(String message, String p1, String p2) {
        this.message = message;
        Date now = new Date();
        Formatter formatter = new Formatter();
        formatter.format("Date: %ta %<tb %<td %<tT %<tY %<tz", now);
        this.date = formatter.toString();
        formatter.close();
        this.parent1 = p1;
        Commit parentCommit1 = Utils.readCommit(p1);
        this.blobs = new TreeMap<>(parentCommit1.blobs);
        if (p2 != null) {
            this.parent2 = p2;
        }
    }

    public static String getId(Commit c) {
        List<String> vals;
        if (c.blobs != null) {
            vals = new ArrayList<>(c.blobs.values());
        } else {
            vals = new ArrayList<>();
        }
        vals.add(c.message);
        vals.add(c.date);
        if (c.parent1 != null) {
            vals.add(c.parent1);
        }
        if (c.parent2 != null) {
            vals.add(c.parent2);
        }
        vals.add("commit");
        c.id = Utils.sha1(vals.toArray(new Object[vals.size()]));
        return c.id;
    }

    /** Search for the lowest common ancestor of two commits */
    public static String getSplitPoint(String aId, String bId) {
        String tmp;
        Commit cur;
        int oldSize, newSize;

        Set<String> ancestorOfA = new HashSet<>();
        Set<String> ancestorOfB = new HashSet<>();
        Queue<String> q = new LinkedList<>();

        tmp = aId;
        q.offer(tmp);
        oldSize = 1;
        while (!q.isEmpty()) {
            newSize = 0;
            for (int i = 0; i < oldSize; i++) {
                tmp = q.poll();
                if (tmp.equals(bId)) {
                    return tmp;
                }
                cur = Utils.readCommit(tmp);
                if (cur.parent1 != null && !ancestorOfA.contains(cur.parent1)) {
                    q.offer(cur.parent1);
                    ancestorOfA.add(cur.parent1);
                    newSize++;
                }

                if (cur.parent2 != null && !ancestorOfA.contains(cur.parent2)) {
                    q.offer(cur.parent2);
                    ancestorOfA.add(cur.parent2);
                    newSize++;
                }
            }
            oldSize = newSize;
        }

        tmp = bId;
        q.offer(tmp);
        oldSize = 1;
        while (!q.isEmpty()) {
            newSize = 0;
            for (int i = 0; i < oldSize; i++) {
                tmp = q.poll();
                if (tmp.equals(aId) || ancestorOfA.contains(tmp)) {
                    return tmp;
                }
                cur = Utils.readCommit(tmp);

                if (cur.parent1 != null && !ancestorOfB.contains(cur.parent1)) {
                    q.offer(cur.parent1);
                    ancestorOfB.add(cur.parent1);
                    newSize++;
                }

                if (cur.parent2 != null && !ancestorOfB.contains(cur.parent2)) {
                    q.offer(cur.parent2);
                    ancestorOfB.add(cur.parent2);
                    newSize++;
                }
            }
            oldSize = newSize;
        }

        return null;
    }

    public void outputLog() {
        System.out.println("===");
        System.out.println("commit " + this.id);

        if (parent2 != null) {
            System.out.println("Merge: " + parent1.substring(0, 7) + " " + parent2.substring(0, 7));
        }

        System.out.println(this.date);
        System.out.println(this.message);
        System.out.println();
    }

    public void writeCommit() {
        Utils.saveCommit(this, this.id);
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        StringBuilder ret = new StringBuilder("Commit: \nMessage: " + this.message + "\n");
        if (parent1 != null) {
            ret.append("parent1: ").append(parent1).append("\n");
        }
        if (parent2 != null) {
            ret.append("parent2: ").append(parent2).append("\n");
        }
        ret.append(this.date).append("\n");
        for (Map.Entry<String, String> e : this.blobs.entrySet()) {
            ret.append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
        }
        return ret.toString();
    }
}

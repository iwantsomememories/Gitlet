package gitlet;

import java.io.Serializable;
import java.util.*;


/** Represents a gitlet commit object.
 *  Record the latest commits of the current branch and all branches.
 *
 *  @author fqcd
 */
public class Branches implements Serializable {

    /** Current branch name. */
    private String curBranch;

    /** The latest commit uid of the current branch. */
    private String curCommit;

    /** Mapping of each branch name to the corresponding latest commit. */
    private HashMap<String, String> refs;

    public Branches(String uid) {
        curBranch = "master";
        curCommit = uid;
        refs = new HashMap<>();
        refs.put(curBranch, curCommit);
    }

    public void writeBranches() {
        Utils.writeObject(Repository.BRANCHES, this);
    }

    public void update(String uid) {
        curCommit = uid;
        refs.put(curBranch, curCommit);
    }

    public boolean newBranch(String name) {
        if (refs.containsKey(name)) {
            return false;
        }
        refs.put(name, curCommit);
        this.writeBranches();
        return true;
    }

    public String getCommit(String branchName) {
        if (!refs.containsKey(branchName)) {
            return null;
        }
        return refs.get(branchName);
    }

    public boolean removeBranch(String branchName) {
        if (!refs.containsKey(branchName)) {
            return false;
        }
        refs.remove(branchName);
        writeBranches();
        return true;
    }

    public List<String> getBranchNames() {
        List<String> res = new ArrayList<>(refs.keySet());
        Collections.sort(res);
        return res;
    }

    public String getCurBranch() {
        return curBranch;
    }

    public String getCurCommit() {
        return curCommit;
    }

    public void setCurBranch(String branch) {
        this.curBranch = branch;
    }

    public void setCurCommit(String curCommit) {
        this.curCommit = curCommit;
    }

    @Override
    public String toString() {
        StringBuffer ret = new StringBuffer("Branches: \n");
        for (Map.Entry<String, String> e : this.refs.entrySet()) {
            ret.append(e.getKey()).append(" : ").append(e.getValue()).append("\n");
        }
        return ret.toString();
    }
}

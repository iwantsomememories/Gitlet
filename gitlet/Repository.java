package gitlet;

import java.io.File;
import java.util.*;

import static gitlet.Utils.*;


/** Represents a gitlet repository.
 *  Contains the specific implementation of each command.
 *
 *  @author fqcd
 */
public class Repository {
    /** The current working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** The .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");

    /** The objects' directory, which contains both commits and blobs. */
    public static final File OBJECT_DIR = join(GITLET_DIR, "objects");

    /** The commits' directory. */
    public static final File COMMIT_DIR = join(OBJECT_DIR, "commits");

    /** The blobs' directory. */
    public static final File BLOB_DIR = join(OBJECT_DIR, "blobs");

    /** The refs file, which contains maps branch names to latest Commit UID */
    public static final File BRANCHES = join(GITLET_DIR, "branches");

    /** The stage_area file, which contains maps of tracked file names to UID */
    public static final File STAGE_AREA = join(GITLET_DIR, "index");

    /** Initialize the warehouse and create some necessary files. --init */
    public static void setUpPersistence() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system already exists in the current directory.");
            return;
        }
        if (!COMMIT_DIR.mkdirs() || !BLOB_DIR.mkdirs()) {
            System.out.println("Failed to create directories");
        }
        Commit first = new Commit("initial commit");
        String id = Commit.getId(first);
        first.writeCommit();

        // Initialize BRANCHES file, a hash map.
        Branches branches = new Branches(id);
        branches.writeBranches();

        // Initialize STAGE_AREA file.
        Stage stage = new Stage();
        writeObject(STAGE_AREA, stage);
    }

    /** Stage file according to fileName --add */
    public static void stageFile(String fileName) {
        File f =  join(CWD, fileName);
        if (!f.exists()) {
            System.out.println("File does not exist.");
            return;
        }
        String fid = Stage.getId(f);

        Branches branches = readObject(BRANCHES, Branches.class);
        String curCommitId = branches.getCurCommit();
        Commit curCommit = readCommit(curCommitId);
        Stage stage = readObject(STAGE_AREA, Stage.class);

        if (curCommit.blobs.containsKey(fileName) && curCommit.blobs.get(fileName).equals(fid)) {
            stage.index.remove(fileName);
        } else {
            stage.trackFile(f);
        }

        stage.writeStage();
    }

    /** Generate new commit. --commit */
    public static void newCommit(String message) {
        Stage stage = readObject(STAGE_AREA, Stage.class);
        if (stage.index.isEmpty()) {
            System.out.println("No changes added to the commit.");
            return;
        }
        Branches branches = readObject(BRANCHES, Branches.class);
        Commit latest = new Commit(message, branches.getCurCommit(), null);

        stage.finalCommit(latest);

        String uid = Commit.getId(latest);
        latest.writeCommit();

        branches.update(uid);
        branches.writeBranches();
    }

    /** Remove file. --rm */
    public static void removeFile(String fileName) {
        Stage stage = readObject(STAGE_AREA, Stage.class);
        Branches branches = readObject(BRANCHES, Branches.class);
        Commit commit = readCommit(branches.getCurCommit());

        if (!stage.index.containsKey(fileName) && !commit.blobs.containsKey(fileName)) {
            System.out.println("No reason to remove the file.");
            return;
        }

        if (stage.index.containsKey(fileName)) {
            if (!stage.index.get(fileName).equals(Stage.REMOVAL)) {
                stage.index.remove(fileName);
                stage.writeStage();
            }
        } else {
            stage.index.put(fileName, Stage.REMOVAL);
            File f = join(CWD, fileName);
            if (f.exists() && Stage.getId(f).equals(commit.blobs.get(fileName))) {
                restrictedDelete(fileName);
            }
            stage.writeStage();
        }
    }

    /** Check out the file in the commit with the given id according to the file name.
     * If id is null, it represents the current commit. --checkout */
    public static void checkoutFile(String commitId, String fileName) {
        Commit commit = null;
        if (commitId == null) {
            Branches branches = readObject(BRANCHES, Branches.class);
            commit = readCommit(branches.getCurCommit());
        } else {
            commit = readCommit(commitId);
        }

        if (commit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }

        if (!commit.blobs.containsKey(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }

        String fileUid = commit.blobs.get(fileName);
        File file = getObjectFile(fileUid, BLOB_DIR);
        byte[] contents = readContents(file);

        writeContents(join(CWD, fileName), (Object) contents);
    }

    public static void checkoutBranch(String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        String commitId = branches.getCommit(branchName);
        if (commitId == null) {
            System.out.println("No such branch exists.");
            return;
        } else if (branches.getCurBranch().equals(branchName)) {
            System.out.println("No need to checkout the current branch.");
            return;
        }

        Commit newCommit = Utils.readCommit(commitId);
        Commit oldCommit = Utils.readCommit(branches.getCurCommit());

        if (switchCommit(newCommit, oldCommit)) {
            Stage stage = readObject(STAGE_AREA, Stage.class);
            stage.index.clear();
            stage.writeStage();

            branches.setCurBranch(branchName);
            branches.setCurCommit(commitId);
            branches.writeBranches();
        }
    }

    /** Switch the contents of files in the current folder from one commit to another. */
    public static boolean switchCommit(Commit newCommit, Commit oldCommit) {
        Set<String> exclFiles = new HashSet<>(newCommit.blobs.keySet());
        Set<String> deleteFiles = new HashSet<>();
        for (String oldFile : oldCommit.blobs.keySet()) {
            if (!exclFiles.remove(oldFile)) {
                deleteFiles.add(oldFile);
            }
        }

        List<String> workingFiles = plainFilenamesIn(CWD);
        for (String workingFile : workingFiles) {
            if (exclFiles.contains(workingFile)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return false;
            }
        }

        for (String deleteFile : deleteFiles) {
            restrictedDelete(new File(CWD, deleteFile));
        }

        for (Map.Entry<String, String> entry : newCommit.blobs.entrySet()) {
            String fileName = entry.getKey();
            String blobId = entry.getValue();
            byte[] content = readContents(getObjectFile(blobId, BLOB_DIR));
            writeContents(join(CWD, fileName), content);
        }

        return true;
    }

    /** Traverse all first parent commits starting from the current commit and
     * print the commit information. --log */
    public static void printCurLog() {
        Branches branches = readObject(BRANCHES, Branches.class);
        Commit commit = readCommit(branches.getCurCommit());
        String commitId = branches.getCurCommit();
        commit.outputLog();

        while (commit.parent1 != null) {
            commitId = commit.parent1;
            commit = readCommit(commitId);
            commit.outputLog();
        }
    }

    /** Print all commit information in unordered order. --global-log */
    public static void printAllLog() {
        String[] subDirs = COMMIT_DIR.list();
        for (String subDir : subDirs) {
            File sub = join(COMMIT_DIR, subDir);
            List<String> commitIds = plainFilenamesIn(sub);

            for (String commitId : commitIds) {
                String fullId = subDir + commitId;
                Commit commit = readObject(join(sub, commitId), Commit.class);
                commit.outputLog();
            }
        }
    }

    /** Print all commit ids that contain the given commit message. --find */
    public static void findLog(String message) {
        String[] subDirs = COMMIT_DIR.list();
        boolean exist = false;
        for (String subDir : subDirs) {
            File sub = join(COMMIT_DIR, subDir);
            List<String> commitIds = plainFilenamesIn(sub);

            for (String commitId : commitIds) {
                String fullId = subDir + commitId;
                Commit commit = readObject(join(sub, commitId), Commit.class);
                if (commit.getMessage().contains(message)) {
                    System.out.println(fullId);
                    exist = true;
                }
            }
        }

        if (!exist) {
            System.out.println("Found no commit with that message.");
        }
    }

    /** Create new branch with the given name. --branch */
    public static void newBranch(String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        if (!branches.newBranch(branchName)) {
            System.out.println("A branch with that name already exists.");
            return;
        }
    }

    /** Display the current status of the gitlet repository. --status */
    public static void showStatus() {
        StringBuffer output = new StringBuffer();
        output.append("=== Branches ===\n");

        Branches branches = readObject(BRANCHES, Branches.class);
        List<String> branchNames = branches.getBranchNames();
        output.append("*").append(branches.getCurBranch()).append("\n");
        for (String name : branchNames) {
            if (!name.equals(branches.getCurBranch())) {
                output.append(name).append("\n");
            }
        }
        output.append("\n");

        Stage stage = readObject(STAGE_AREA, Stage.class);
        List<String> stagedFiles = new ArrayList<>();
        List<String> removedFiles = new ArrayList<>();
        for (Map.Entry<String, String> e : stage.index.entrySet()) {
            if (e.getValue().equals(Stage.REMOVAL)) {
                removedFiles.add(e.getKey());
            } else {
                stagedFiles.add(e.getKey());
            }
        }
        Collections.sort(stagedFiles);
        Collections.sort(removedFiles);

        output.append("=== Staged Files ===\n");
        for (String stageFile : stagedFiles) {
            output.append(stageFile).append("\n");
        }
        output.append("\n");

        output.append("=== Removed Files ===\n");
        for (String removeFile : removedFiles) {
            output.append(removeFile).append("\n");
        }
        output.append("\n");

        Commit commit = readCommit(branches.getCurCommit());
        Set<String> workingFiles = new HashSet<>(plainFilenamesIn(CWD));
        List<String> modifiedFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();
        for (Map.Entry<String, String> e : commit.blobs.entrySet()) {
            String name = e.getKey();
            if (!stage.index.containsKey(name)) {
                if (workingFiles.contains(name)) {
                    byte[] contents = readContents(join(CWD, name));
                    String workingId = sha1(contents, name);
                    if (!workingId.equals(e.getValue())) {
                        modifiedFiles.add(name);
                    }
                } else {
                    deletedFiles.add(name);
                }
            }
        }
        for (Map.Entry<String, String> e : stage.index.entrySet()) {
            String name = e.getKey();
            if (!e.getValue().equals(Stage.REMOVAL)) {
                if (workingFiles.contains(name)) {
                    byte[] contents = readContents(join(CWD, name));
                    String workingId = sha1(contents, name);
                    if (!workingId.equals(e.getValue())) {
                        modifiedFiles.add(name);
                    }
                } else {
                    deletedFiles.add(name);
                }
            }
        }
        Collections.sort(modifiedFiles);
        Collections.sort(deletedFiles);

        output.append("=== Modifications Not Staged For Commit ===\n");
        int i = 0, j = 0;
        while (i < modifiedFiles.size() && j < deletedFiles.size()) {
            if (modifiedFiles.get(i).compareTo(deletedFiles.get(j)) < 0) {
                output.append(modifiedFiles.get(i)).append(" (modified)\n");
                i++;
            } else {
                output.append(deletedFiles.get(j)).append(" (deleted)\n");
                j++;
            }
        }
        while (i < modifiedFiles.size()) {
            output.append(modifiedFiles.get(i)).append(" (modified)\n");
            i++;
        }
        while (j < deletedFiles.size()) {
            output.append(deletedFiles.get(j)).append(" (deleted)\n");
            j++;
        }
        output.append("\n");

        output.append("=== Untracked Files ===\n");
        List<String> untrackedFiles = new ArrayList<>();
        for (String name : workingFiles) {
            if (!commit.blobs.containsKey(name) && !stage.index.containsKey(name)) {
                untrackedFiles.add(name);
            }
        }
        Collections.sort(untrackedFiles);
        for (String untrackedFile : untrackedFiles) {
            output.append(untrackedFile).append("\n");
        }
        output.append("\n");

        System.out.printf(output.toString());
    }

    /** Deletes the branch with the given name. --rm-branch */
    public static void removeBranch(String branchName) {
        Branches branches = readObject(BRANCHES, Branches.class);
        if (branches.getCurBranch().equals(branchName)) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        if (!branches.removeBranch(branchName)) {
            System.out.println("A branch with that name does not exist.");
        }
    }

    /** Checks out all the files tracked by the given commit. --reset */
    public static void reset(String commitId) {
        Commit newCommit = readCommit(commitId);
        if (newCommit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }

        Branches branches = readObject(BRANCHES, Branches.class);
        Commit oldCommit = readCommit(branches.getCurCommit());

        if (switchCommit(newCommit, oldCommit)) {
            branches.update(newCommit.id);
            branches.writeBranches();

            Stage stage = readObject(STAGE_AREA, Stage.class);
            stage.index.clear();
            stage.writeStage();
        }
    }

    /** Merge the branch with the given branch name into the current branch. --merge */
    public static void mergeBranch(String branchName) {
        Stage stage = readObject(STAGE_AREA, Stage.class);
        if (!stage.index.isEmpty()) {
            System.out.println("You have uncommitted changes.");
            return;
        }

        Branches branches = readObject(BRANCHES, Branches.class);
        if (branchName.equals(branches.getCurBranch())) {
            System.out.println("Cannot merge a branch with itself.");
            return;
        } else if (branches.getCommit(branchName) == null) {
            System.out.println("A branch with that name does not exist.");
            return;
        }

        Commit curCommit = readCommit(branches.getCurCommit());
        Commit targetCommit = readCommit(branches.getCommit(branchName));

        // Checks if there are untracked files that will be overwritten.
        Set<String> curFiles = new HashSet<>(curCommit.blobs.keySet());
        Set<String> targetFiles = new HashSet<>(targetCommit.blobs.keySet());
        List<String> workingFiles = plainFilenamesIn(CWD);
        for (String workingFile : workingFiles) {
            if (!curFiles.contains(workingFile) && targetFiles.contains(workingFile)) {
                System.out.println("There is an untracked file in the way; delete it, or add and commit it first.");
                return;
            }
        }

        String sp = Commit.getSplitPoint(branches.getCurCommit(), branches.getCommit(branchName));
        if (sp == null) {
            System.out.println("mergeBranch: SplitPoint is null!!!");
            return;
        }

        if (sp.equals(branches.getCommit(branchName))) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return;
        }

        if (sp.equals(branches.getCurCommit())) {
            checkoutBranch(branchName);
            System.out.println("Current branch fast-forwarded.");
            return;
        }

        Commit lca = readCommit(sp);
        boolean conflictFlag = false;
        File stagedFile;
        for (Map.Entry<String, String> e : lca.blobs.entrySet()) {
            String fileName = e.getKey();
            String blobId = e.getValue();
            targetFiles.remove(fileName);

            if (curCommit.blobs.containsKey(fileName) && targetCommit.blobs.containsKey(fileName)) {
                if (curCommit.blobs.get(fileName).equals(targetCommit.blobs.get(fileName))) {
                    continue;
                } else if (targetCommit.blobs.get(fileName).equals(blobId)) {
                    continue;
                } else if (curCommit.blobs.get(fileName).equals(blobId)) {
                    stagedFile = join(CWD, fileName);
                    byte[] contents = readContents(getObjectFile(targetCommit.blobs.get(fileName), BLOB_DIR));
                    writeContents(stagedFile, contents);
                    stage.trackFile(stagedFile);
                } else {
                    conflictFlag = true;
                    stagedFile = fixConflict(getObjectFile(curCommit.blobs.get(fileName), BLOB_DIR),
                            getObjectFile(targetCommit.blobs.get(fileName), BLOB_DIR),
                            fileName);
                    stage.trackFile(stagedFile);
                }
            } else if (!curCommit.blobs.containsKey(fileName) && targetCommit.blobs.containsKey(fileName)) {
                if (targetCommit.blobs.get(fileName).equals(blobId)) {
                    continue;
                } else {
                    conflictFlag = true;
                    stagedFile = fixConflict(null,
                            getObjectFile(targetCommit.blobs.get(fileName), BLOB_DIR),
                            fileName);
                    stage.trackFile(stagedFile);
                }
            } else if (curCommit.blobs.containsKey(fileName) && !targetCommit.blobs.containsKey(fileName)) {
                if (curCommit.blobs.get(fileName).equals(blobId)) {
                    stage.index.put(fileName, Stage.REMOVAL);
                    stagedFile = join(CWD, fileName);
                    if (stagedFile.exists() && Stage.getId(stagedFile).equals(blobId)) {
                        restrictedDelete(stagedFile);
                    }
                } else {
                    conflictFlag = true;
                    stagedFile = fixConflict(getObjectFile(curCommit.blobs.get(fileName), BLOB_DIR),
                            null,
                            fileName);
                    stage.trackFile(stagedFile);
                }
            }
        }

        for (String name : targetFiles) {
            if (!curFiles.contains(name)) {
                stagedFile = join(CWD, name);
                byte[] contents = readContents(getObjectFile(targetCommit.blobs.get(name), BLOB_DIR));
                writeContents(stagedFile, contents);
                stage.trackFile(stagedFile);
            } else if (!curCommit.blobs.get(name).equals(targetCommit.blobs.get(name))) {
                conflictFlag = true;
                stagedFile = fixConflict(getObjectFile(curCommit.blobs.get(name), BLOB_DIR),
                        getObjectFile(targetCommit.blobs.get(name), BLOB_DIR),
                        name);
                stage.trackFile(stagedFile);
            }
        }

        String message = "Merged " + branchName + " into " + branches.getCurBranch() + ".";
        Commit mergedCommit = new Commit(message, branches.getCurCommit(), branches.getCommit(branchName));
        stage.finalCommit(mergedCommit);

        String uid = Commit.getId(mergedCommit);
        mergedCommit.writeCommit();

        branches.setCurCommit(uid);
        branches.writeBranches();

        if (conflictFlag) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /** Dealing with merge conflicts in a single file. */
    private static File fixConflict(File curBlob, File otherBlob, String fileName) {
        String headLine = "<<<<<<< HEAD\n";
        String middleLine = "=======\n";
        String tailLine = ">>>>>>>\n";
        File stagedFile = join(CWD, fileName);

        if (curBlob == null) {
            byte[] contentOfOther = readContents(otherBlob);
            writeContents(stagedFile, headLine, middleLine, contentOfOther, tailLine);
        } else if (otherBlob == null) {
            byte[] contentOfCur = readContents(curBlob);
            writeContents(stagedFile, headLine, contentOfCur, middleLine, tailLine);
        } else {
            byte[] contentOfOther = readContents(otherBlob);
            byte[] contentOfCur = readContents(curBlob);
            writeContents(stagedFile, headLine, contentOfCur, middleLine, contentOfOther, tailLine);
        }

        return stagedFile;
    }

    // these for Debug.
    public static void printStage() {
        Stage stage = readObject(STAGE_AREA, Stage.class);
        System.out.println(stage);
    }

    public static void printBranches() {
        Branches branches = readObject(BRANCHES, Branches.class);
        System.out.println(branches);
    }

    public static void printCurCommit() {
        Branches branches = readObject(BRANCHES, Branches.class);
        Commit c = readCommit(branches.getCurCommit());
        System.out.println(c);
    }
}

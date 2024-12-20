package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 *  @author Ao Yan
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }
        String firstArg = args[0];
        String fileName;
        String commitId;
        String message;
        String branchName;
        switch (firstArg) {
            case "init":
                Repository.setUpPersistence();
                break;
            case "add":
                if (validCheck(args)) {
                    fileName = args[1];
                    Repository.stageFile(fileName);
                }
                break;
            case "commit":
                if (validCheck(args)) {
                    message = args[1];
                    if (message.isEmpty()) {
                        System.out.println("Please enter a commit message.");
                        return;
                    }
                    Repository.newCommit(message);
                }
                break;
            case "rm":
                if (validCheck(args)) {
                    fileName = args[1];
                    Repository.removeFile(fileName);
                }
                break;
            case "checkout":
                if (!Repository.GITLET_DIR.exists()) {
                    System.out.println("Not in an initialized Gitlet directory.");
                    return;
                }
                if (args.length < 2 || args.length > 4) {
                    System.out.println("Incorrect operands.");
                    return;
                }
                if (args.length == 2) {
                    branchName = args[1];
                    Repository.checkoutBranch(branchName);
                } else if (args.length == 3) {
                    if (!args[1].equals("--")) {
                        System.out.println("Incorrect operands.");
                        return;
                    }
                    fileName = args[2];
                    Repository.checkoutFile(null, fileName);
                } else {
                    if (!args[2].equals("--")) {
                        System.out.println("Incorrect operands.");
                        return;
                    }
                    fileName = args[3];
                    commitId = args[1];
                    Repository.checkoutFile(commitId, fileName);
                }
                break;
            case "log":
                if (validCheck()) {
                    Repository.printCurLog();
                }
                break;
            case "global-log":
                if (validCheck()) {
                    Repository.printAllLog();
                }
                break;
            case "find":
                if (validCheck(args)) {
                    message = args[1];
                    Repository.findLog(message);
                }
                break;
            case "branch":
                if (validCheck(args)) {
                    branchName = args[1];
                    Repository.newBranch(branchName);
                }
                break;
            case "status":
                if (validCheck()) {
                    Repository.showStatus();
                }
                break;
            case "rm-branch":
                if (validCheck(args)) {
                    branchName = args[1];
                    Repository.removeBranch(branchName);
                }
                break;
            case "reset":
                if (validCheck(args)) {
                    commitId = args[1];
                    Repository.reset(commitId);
                }
                break;
            case "merge":
                if (validCheck(args)) {
                    branchName = args[1];
                    Repository.mergeBranch(branchName);
                }
                break;
            case "pS":
                Repository.printStage();
                break;
            case "pB":
                Repository.printBranches();
                break;
            case "pC":
                Repository.printCurCommit();
                break;
            default:
                System.out.println("No command with that name exists.");
                break;
        }
    }

    public static boolean validCheck(String[] args) {
        if (!Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return false;
        }

        if (args.length != 2) {
            System.out.println("Incorrect operands.");
            return false;
        }

        return true;
    }

    public static boolean validCheck() {
        if (!Repository.GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            return false;
        }

        return true;
    }
}

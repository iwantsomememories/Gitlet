# Gitlet Design Document

**Name**: fqcd

## Classes and Data Structures



### Main

gitlet的入口。读取命令行参数并调用对应函数，相关函数定义在其他文件中。此外，main还会检查命令行参数的合法性。

#### Fileds

无



### Repository

该类表示版本库，这就是项目的主要逻辑所在。此文件将通过读取/写入正确的文件、设置持久性和额外的错误检查来处理所有实际的`gitlet`命令。

它还将负责在`gitlet`中设置所有的持久化存储。这包括创建 .gitlet文件夹，存储所有 Commit 对象、Blob对象、暂存区以及当前分支的文件夹和文件。

#### Fields

1. `public static final File CWD`：当前工作目录。
2. `public static final File GITLET_DIR`：.gitlet目录。
3. `public static final File OBJECT_DIR`：对象目录，存放Commit对象以及普通文件对象。
4. `public static final File COMMIT_DIR = join(OBJECT_DIR, "commits")`：commit目录，存放commit对象。
5. `public static final File BLOB_DIR = join(OBJECT_DIR, "blobs")`：blob目录，存放blobs。
6. `public static final File BRANCHES`：引用文件，存放各个分支的分支名到最新提交UID的映射，同时包含当前提交及当前分支名。
7. `public static final File STAGE_AREA`：暂存区文件。



### Commit

该类表示将被存储在文件中的一次提交(Commit)，每个提交都拥有唯一的`uid`，因此可以根据`uid`来设置序列化 Commit 对象的文件名。

所有的 Commit 对象都被序列化存储在位于 OBJECT_DIR 目录中的 COMMIT_DIR 中。Commit 类提供了一些有用的方法，这些方法可以根据给定的提交信息生成新提交，按照日志格式输出 Commit，为单个 Commit 对象生成`uid`，将该 Commit 对象写入文件等。

#### Fields

1. `private String message`：提交信息。
2. `private String date`：提交日期，格式为`Date: %tA %<tb %<te %<tT %<tY %<tz`。
3. ` private String parent1`：第一个父提交。
4. `private String parent2`：第二个父提交。
5. `public TreeMap<String, String> blobs;`：该提交的文件名的文件对象的映射。



### Stage

该类表示暂存区域，每个版本库有且仅有一个暂存区域，因此我们使用`index`作为唯一序列化 Stage 对象的文件名。

暂存区域 STAGE_AREA 存储在 GITLET_DIR 目录中。Stage 类提供了一些有用的方法，这些方法可以根据文件对象跟踪文件、生成文件对象`uid`，根据文件名取消跟踪文件，将暂存区域中的内容写入新提交，将 Stage 对象写入文件等。

#### Fileds

1. `public HashMap<String, String> index`：暂存区文件名到文件uid的映射。
1. `public static final String REMOVAL = "removal"`：删除文件标记。



### Branches

该类用于存储各分支到最新提交的映射以及当前分支。每个版本库有且仅有一个当前分支，因此我们使用`branches`作为唯一序列化 Branches 对象的文件名。

暂存区域 BRANCHES 存储在 GITLET_DIR 目录中。Branches 类提供了一些有用的方法，这些方法可以根据给定分支名生成新分支、移除分支或返回该分支最新提交`uid`，根据给定`uid`更新当前分支最新提交，将 Branches 对象写入文件等。

1. `private String curBranch`：当前分支名。
2. `private String curCommit`：当前提交uid。
3. `private HashMap<String, String> refs`：分支名到对应最新提交uid的映射。



### Utils

该类包含一些有用的工具方法，用于从文件中读取/写入对象或普通文件内容，生成对象的`sha-1`哈希字符串，查看给定目录下的文件，删除文件，以及在发生错误时报告错误。

#### Fields

1. `static final int UID_LENGTH`：表示`sha-1`哈希字符串长度。



## Algorithms

1. 实现`merge`命令需要查找两个 Commit 对象的最近公共祖先。Commit 类中方法`getSplitPoint`用于解决该问题，方法签名如下：`public static String getSplitPoint(String aId, String bId)`。

   具体思路如下：

   - 首先根据`aId, bId`分别读取对应提交对象`a, b`；
   - 采用广度优先搜索遍历提交`a`的所有父提交，并记录在集合`ancestorOfA`中，遍历过程中若遇到提交`b`，则返回`b`；
   - 然后采用广度有限搜索遍历`b`的所有父提交，遍历过程若遇到提交`a`，则返回`a`，否则若`ancestorOfA`包含该父提交，则返回该父提交。

​	时间复杂度分析：`O(N)`，N为提交总数。

​	空间复杂度分析：`O(N)`，N为提交总数。



## Persistence

```
CWD                         <==== 当前工作文件夹
└── .gitelet                    <==== 所有持久化存储文件存放目录
    ├── branches                <==== 存储当前分支及各分支最新提交的文件
    ├── index					<==== 存储暂存区域的文件
    └── objects                 <==== 存放blob对象以及commit对象的目录
        ├── blobs               <==== 存放blob对象目录
        |   ├── a6
        |	|	├── ...
        |	|	└── ...
        |	└──	...
        └── commits				<==== 存放commit对象目录
			├── 7b
        	|	├── ...
        	|	└── ...
        	└──	...
```

`Repository`将负责所有持久化的操作。它将：

- 如果 `.capers` 文件夹不存在，则创建该文件夹。
- 如果 `objects` 文件夹不存在，则创建该文件夹。
- 如果 `blobs` 文件夹不存在，则创建该文件夹。
- 如果 `commits` 文件夹不存在，则创建该文件夹。
- 如果 `branches` 文件不存在，则创建一个 Branches 对象并写入文件。
- 如果 `index` 文件不存在，则创建一个 Stage 对象并写入文件。

Utils 类将处理各类对象的序列化读取，方法如下：

- `static <T extends Serializable> T readObject(File file, Class<T> expectedClass)`：根据类对象及文件对象读取对应对应类的实例。
- `static byte[] readContents(File file)`：根据文件对象将对应文件内容读取为字节数组。

同时各类包含对应对象的写入方法：

- Stage：`public void writeStage()`
- Branches：`public void writeBranches()`
- Commit：`public void writeCommit()`：该方法调用 Utils 中的方法——`static void saveCommit(Commit commit, String uid)`，取`uid`中的前两个字符作为文件夹名，检查`commits` 文件夹下是否存在该文件夹，不存在则新建，同时取`uid`中的剩余字符作为文件名写入 Commit 对象，存入该文件夹。

对于普通文件，Utils 类采用与 Commit 类相似的方法将文件内容存入 `blobs` 文件夹下，方法如下：

- `static void saveBlob(byte[] contents, String uid)`

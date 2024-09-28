package cn.yl.common.utils;

import com.alibaba.excel.annotation.ExcelProperty;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AList网盘操作工具
 *
 * @author YL
 * @since $!time.currTime()
 */
@SuppressWarnings("all")
public class AList {

    private static Map<String, String> Mappings;


    /**
     * 网盘文件搜索
     *
     * @param licensePlateMap 查询到的集合
     * @param i               索引
     * @param searchList      搜索错误的集合
     * @param searchMap
     * @param path            路径
     * @param password        密码
     * @param scope           类型  0:全部 1:文件 2：文件夹
     * @param page            页码
     * @param per_Page        每页显示数量
     * @param token           token
     * @return
     */
    public static Map<String, Data> search(Map<Integer, String> licensePlateMap, Integer i, String path, String password,
                                           long scope, long page, long per_Page, String token, String searchError,
                                           List<Search> searchList, Map<String, Data> searchMap) throws IOException {
        //todo 处理数据库中查到的可能还包含影片名称
        String regex = "\\b(?:[a-zA-Z]*[-\\d\\s0-9a-zA-Z]*[-\\d])";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(licensePlateMap.get(i));
        if (matcher.find()) {
            String group = matcher.group();
            //构建搜索
            Search search = Builder.of(Search::new)
                    .with(Search::setKeywords, group)
                    .with(Search::setParent, path)
                    .with(Search::setPassword, password)
                    .with(Search::setScope, scope)
                    .with(Search::setPage, page)
                    .with(Search::setPer_Page, per_Page)
                    .build();
            String response = HttpUtil.httpPost("http://localhost:5244/api/fs/search", JsonUtil.toJSONString(search), new HashMap<String, String>() {{
                this.put("Authorization", token);
            }});
            Data data = JsonUtil.parseObject(response, Result.class).getData();
            if (!data.getContent().isEmpty()) {
                searchMap.put(licensePlateMap.get(i), data);
            } else {
                searchList.add(search);
            }
        }
        if (!searchList.isEmpty()) {
            //将命名失败的写入一个文件中以备后面写入
            Files.write(new File(searchError).toPath(), JsonUtil.toByteArray(searchList));
        }
        return searchMap;
    }

    /**
     * AList重命名
     *
     * @param searchList      搜索到的集合
     * @param actorNameMap    演员名
     * @param licensePlateMap 车牌名
     * @param i               索引
     * @param token           token
     * @param errorRename     命名失败的文件路径
     * @throws Exception
     */
    public static void rename(Map<String, Data> searchList, Map<Integer, String> actorNameMap, Map<Integer, String> licensePlateMap, Integer i, String token, String errorRename, Map<String, String> mappings) throws IOException {
        Map<String, List<Rename>> renameMap = new HashMap<>();
        List<Rename> batchRename = new ArrayList<>();
        Data data = searchList.get(licensePlateMap.get(i));
        if (data == null) return;
        Map<String, List<Content>> collect = data.getContent().stream().collect(Collectors.groupingBy(Content::getParent));
        collect.forEach((key, value) -> {
            List<RenameObject> renameObjects = new ArrayList<>();
            Map<String, Content> fileMap = value.stream().collect(Collectors.toMap(Content::getName, Function.identity(), (o1, o2) -> o1));
            fileMap.forEach((k, v) -> {
                if (actorNameMap.get(i) != null) {
                    if (!v.getName().contains(actorNameMap.get(i))) {
                        RenameObject build = Builder.of(RenameObject::new)
                                .with(RenameObject::setSrc_Name, v.getName())
                                .with(RenameObject::setNew_Name, contains(actorNameMap.get(i), v.getName(), mappings))
                                .build();
                        renameObjects.add(build);
                    }
                }
            });
            if (!renameObjects.isEmpty()) {
                batchRename.add(Builder.of(Rename::new)
                        .with(Rename::setRename_Objects, renameObjects)
                        .with(Rename::setSrc_Dir, key)
                        .build());
            }
        });
        if (!batchRename.isEmpty()) {
            collect.forEach((key, value) -> {
                renameMap.put(key, batchRename);
            });
        }
        List<Rename> error = new ArrayList<>();
        renameMap.forEach((k, v) -> {
            List<RenameObject> renameList = new ArrayList<>();
            for (Rename rename : v) {
                for (RenameObject renameObject : rename.rename_Objects) {
                    renameList.add(renameObject);
                }
            }
            String rename = JsonUtil.toJSONString(Builder.of(Rename::new)
                    .with(Rename::setSrc_Dir, k)
                    .with(Rename::setRename_Objects, renameList)
                    .build());
            Result result = JsonUtil.parseObject(HttpUtil.httpPost("http://localhost:5244/api/fs/batch_rename", rename,
                    new HashMap<String, String>() {{
                        this.put("Authorization", token);
                    }}), Result.class);
            if (result.getCode() != 200) {
                error.add(Builder.of(Rename::new)
                        .with(Rename::setSrc_Dir, k)
                        .with(Rename::setRename_Objects, renameList)
                        .build());
            }
        });
        if (!error.isEmpty()) {
            //将命名失败的写入一个文件中以备后面写入
            Files.write(new File(errorRename).toPath(), JsonUtil.toByteArray(error));
        }
    }

    /**
     * 官方重命名
     *
     * @param searchList
     * @param list
     * @param mappings
     * @param officialRenames 需要重命名的集合
     * @return 可用于生成一个重命名模版
     */
    public static List<OfficialRename> rename(Map<String, Data> searchList, Map<Integer, String> actorNameMap, Map<Integer, String> licensePlateMap, Integer i, Map<String, String> mappings, List<OfficialRename> officialRenames) {
        Data data = searchList.get(licensePlateMap.get(i));
        if (data == null) return null;
        Map<String, List<Content>> collect = data.getContent().stream().collect(Collectors.groupingBy(Content::getParent));
        collect.forEach((key, value) -> {
            Map<String, Content> fileMap = value.stream().collect(Collectors.toMap(Content::getName, Function.identity(), (o1, o2) -> o1));
            fileMap.forEach((k, v) -> {
                if (actorNameMap.get(i) != null) {
                    if (!v.getName().contains(actorNameMap.get(i))) {
                        OfficialRename build = Builder.of(OfficialRename::new)
                                .with(OfficialRename::setSrc_Name, v.getName())
                                .with(OfficialRename::setNew_Name, contains(actorNameMap.get(i), v.getName(), mappings))
                                .build();
                        officialRenames.add(build);
                    }
                }
            });
        });
        for (OfficialRename officialRename : officialRenames) {
            officialRename.setNew_Name(officialRename.getNew_Name().split("\\.")[0]);
        }
        return officialRenames;
    }

    /**
     * 处理文件名称
     *
     * @param actorName
     * @param srcName
     * @param mappings  映射
     * @return
     */
    private static String contains(String actorName, String srcName, Map<String, String> mappings) {
        String[] split = srcName.split("\\.");
        if (actorName == null) return split[0];
        if (srcName.contains(actorName)) return split[0];
        if (mappings == null) return srcName;
        StringBuffer sb = new StringBuffer();
        mappings.forEach((mapping, value) -> {
            if (srcName.split("(\\b(?:[a-zA-Z]*[-_\\d\\sa-zA-Z]*[-_\\d]))")[0].contains(mapping)) {
                sb.append(value).append(" ");
            }
        });
        String fileName = srcName.replaceAll(srcName.split("(\\b(?:[a-zA-Z]*[-_\\d\\sa-zA-Z]*[-_\\d]))")[0], "") + sb.toString();
        return ("".equals(sb.toString()) ? split[0] + " " + actorName : fileName) + "." + split[1];
    }

    /**
     * 网盘文件移动
     *
     * @param searchList   搜索到的集合
     * @param srcPrefix    源文件夹路径 需要替换的原路径
     * @param targetPrefix 目标路径
     * @param token        token
     * @param errorMove    移动失败的文件路径
     */
    public static void move(Map<String, Data> searchList, String srcPrefix, String targetPrefix, String token, String errorMove) throws Exception {
        List<Move> batchMove = new ArrayList<>();
        searchList.forEach((k, v) -> {
            //键-路径
            Map<String, List<Content>> collect = v.getContent().stream().collect(Collectors.groupingBy(Content::getParent));
            collect.forEach((key, value) -> {
                List<Content> moveList = value.stream().filter(move -> !move.getParent().contains(targetPrefix))
                        .collect(Collectors.toList());

                if (!moveList.isEmpty()) {
                    Move move = Builder.of(Move::new)
                            .with(Move::setSrc_dir, key)
                            .with(Move::setDst_dir, key.replace(srcPrefix, targetPrefix))
                            .with(Move::setNames, moveList.stream().map(Content::getName).collect(Collectors.toList()))
                            .build();
                    batchMove.add(move);
                }
            });
        });
        List<Move> error = new ArrayList<>();
        batchMove.forEach(m -> {
            Result result = JsonUtil.parseObject(HttpUtil.httpPost("http://localhost:5244/api/fs/batch_move", JsonUtil.toJSONString(m), new HashMap<String, String>() {{
                this.put("Authorization", token);
            }}), Result.class);
            if (result.getCode() != 200) {
                error.add(m);
            }
        });
        if (!error.isEmpty()) {
            //将命名失败的写入一个文件中以备后面写入
            Files.write(new File(errorMove).toPath(), JsonUtil.toByteArray(error));
        }
    }

    /**
     * 网盘删除文件
     *
     * @param searchList  搜索到的集合
     * @param token       token
     * @param errorDelete 删除失败的文件路径
     * @throws Exception
     */
    public static void delete(Map<String, Data> searchList, List<String> excludeName, String token, String errorDelete) throws Exception {
        List<Delete> batchDelete = new ArrayList<>();
        searchList.forEach((k, v) -> {
            if (v.getContent().size() > 1) {
                List<Content> contents = v.getContent();
                Map<String, List<Content>> collect = v.getContent().stream().collect(Collectors.groupingBy(Content::getParent));
                collect.forEach((key, value) -> {
                    List<String> names = value.stream().filter(content -> !excludeName.contains(content.getName()))
                            .map(Content::getName).collect(Collectors.toList());
                    Delete delete = Builder.of(Delete::new)
                            .with(Delete::setDir, key)
                            .with(Delete::setNames, names)
                            .build();
                    batchDelete.add(delete);
                });
            }
        });
        List<Delete> error = new ArrayList<>();
        batchDelete.forEach(d -> {
            Result result = JsonUtil.parseObject(HttpUtil.httpPost("http://localhost:5244/api/fs/remove", JsonUtil.toJSONString(d), new HashMap<String, String>() {{
                this.put("Authorization", token);
            }}), Result.class);
            if (result.getCode() != 200) {
                error.add(d);
            }
        });
        if (!error.isEmpty()) {
            Files.write(new File(errorDelete).toPath(), JsonUtil.toByteArray(error));
        }
    }

    /**
     * 搜索文件入参
     */
    public static class Search {
        /**
         * 关键词
         */
        private String keywords;
        /**
         * 页数
         */
        private long page;
        /**
         * 搜索目录
         */
        private String parent;
        /**
         * 密码
         */
        private String password;
        /**
         * 每页数目
         */
        private long per_Page;
        /**
         * 搜索类型，0-全部 1-文件夹 2-文件
         */
        private long scope;

        public String getKeywords() {
            return keywords;
        }

        public void setKeywords(String keywords) {
            this.keywords = keywords;
        }

        public long getPage() {
            return page;
        }

        public void setPage(long page) {
            this.page = page;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String parent) {
            this.parent = parent;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public long getPer_Page() {
            return per_Page;
        }

        public void setPer_Page(long per_Page) {
            this.per_Page = per_Page;
        }

        public long getScope() {
            return scope;
        }

        public void setScope(long scope) {
            this.scope = scope;
        }
    }


    /**
     * 响应结果
     */
    public static class Result {
        /**
         * 状态码
         */
        private long code;
        private Data data;
        /**
         * 信息
         */
        private String message;

        public long getCode() {
            return code;
        }

        public void setCode(long code) {
            this.code = code;
        }

        public Data getData() {
            return data;
        }

        public void setData(Data data) {
            this.data = data;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    /**
     * 搜索数据
     */
    public static class Data {
        private List<Content> content;
        /**
         * 总数
         */
        private long total;

        public List<Content> getContent() {
            return content;
        }

        public void setContent(List<Content> value) {
            this.content = value;
        }

        public long getTotal() {
            return total;
        }

        public void setTotal(long value) {
            this.total = value;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "content=" + content +
                    ", total=" + total +
                    '}';
        }
    }

    /**
     * 文件信息类
     */
    public static class Content {
        /**
         * 是否是文件夹
         */
        private boolean isDir;
        /**
         * 文件名
         */
        private String name;
        /**
         * 路径
         */
        private String parent;
        /**
         * 大小
         */
        private long size;
        /**
         * 类型
         */
        private long type;

        public boolean getIsDir() {
            return isDir;
        }

        public void setIsDir(boolean value) {
            this.isDir = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String value) {
            this.name = value;
        }

        public String getParent() {
            return parent;
        }

        public void setParent(String value) {
            this.parent = value;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long value) {
            this.size = value;
        }

        public long getType() {
            return type;
        }

        public void setType(long value) {
            this.type = value;
        }

        @Override
        public String toString() {
            return "Content{" +
                    "isDir=" + isDir +
                    ", name='" + name + '\'' +
                    ", parent='" + parent + '\'' +
                    ", size=" + size +
                    ", type=" + type +
                    '}';
        }
    }

    /**
     * 批量重命名
     */
    public static class Rename {
        private List<RenameObject> rename_Objects;
        /**
         * 源目录
         */
        private String src_Dir;

        public List<RenameObject> getRename_Objects() {
            return rename_Objects;
        }

        public void setRename_Objects(List<RenameObject> rename_Objects) {
            this.rename_Objects = rename_Objects;
        }

        public String getSrc_Dir() {
            return src_Dir;
        }

        public void setSrc_Dir(String src_Dir) {
            this.src_Dir = src_Dir;
        }

        @Override
        public String toString() {
            return "Rename{" +
                    "rename_Objects=" + rename_Objects +
                    ", src_Dir='" + src_Dir + '\'' +
                    '}';
        }
    }

    /**
     * 115官方重命名
     */
    public static class OfficialRename {

        /**
         * 原文件名
         */
        @ExcelProperty(index = 0)
        private String src_Name;
        /**
         * 新文件名
         */
        @ExcelProperty(index = 1)
        private String new_Name;

        public String getSrc_Name() {
            return src_Name;
        }

        public void setSrc_Name(String src_Name) {
            this.src_Name = src_Name;
        }

        public String getNew_Name() {
            return new_Name;
        }

        public void setNew_Name(String new_Name) {
            this.new_Name = new_Name;
        }

        @Override
        public String toString() {
            return "officialRename{" +
                    "src_Name='" + src_Name + '\'' +
                    ", new_Name='" + new_Name + '\'' +
                    '}';
        }
    }

    /**
     * 重命名对象
     */
    public static class RenameObject {
        /**
         * 新文件名
         */
        private String new_Name;
        /**
         * 原文件名
         */
        private String src_Name;

        public String getNew_Name() {
            return new_Name;
        }

        public void setNew_Name(String new_Name) {
            this.new_Name = new_Name;
        }

        public String getSrc_Name() {
            return src_Name;
        }

        public void setSrc_Name(String src_Name) {
            this.src_Name = src_Name;
        }

        @Override
        public String toString() {
            return "RenameObject{" +
                    "new_Name='" + new_Name + '\'' +
                    ", src_Name='" + src_Name + '\'' +
                    '}';
        }
    }

    /**
     * 移动文件
     */
    public static class Move {

        /**
         * 源目录
         */
        private String src_dir;

        /**
         * 目标目录
         */
        private String dst_dir;

        /**
         * 文件名称
         */
        public List<String> names;

        public String getSrc_dir() {
            return src_dir;
        }

        public void setSrc_dir(String src_dir) {
            this.src_dir = src_dir;
        }

        public String getDst_dir() {
            return dst_dir;
        }

        public void setDst_dir(String dst_dir) {
            this.dst_dir = dst_dir;
        }

        public List<String> getNames() {
            return names;
        }

        public void setNames(List<String> names) {
            this.names = names;
        }

        @Override
        public String toString() {
            return "Move{" +
                    "src_dir='" + src_dir + '\'' +
                    ", dst_dir='" + dst_dir + '\'' +
                    ", names=" + names +
                    '}';
        }
    }

    /**
     * 删除文件
     */
    public static class Delete {
        /**
         * 文件名称
         */
        private List<String> names;

        /**
         * 所在目录
         */
        private String dir;

        public List<String> getNames() {
            return names;
        }

        public void setNames(List<String> names) {
            this.names = names;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        @Override
        public String toString() {
            return "Delete{" +
                    "names=" + names +
                    ", dir='" + dir + '\'' +
                    '}';
        }
    }
}

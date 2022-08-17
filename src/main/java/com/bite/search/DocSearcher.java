package com.bite.search;

import com.bite.build.DocInfo;
import com.bite.build.Index;
import com.bite.build.Weight;
import com.bite.config.FileConfig;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import static org.ansj.splitWord.analysis.ToAnalysis.parse;

// 通过这个类，来完成整个的搜索流程
public class DocSearcher {

    // 停用词的文件路径
    private static String STOP_WORD_PATH=null;

    static {
        if (FileConfig.isOnline) {
            STOP_WORD_PATH= "/root/javadoc/stop_word.txt";
        } else {
            STOP_WORD_PATH = "C:\\Users\\rain7\\Desktop\\stop_word.txt";
        }
    }

    private HashSet<String> stopwords = new HashSet<>();

    private void loadStopWord() {
        // 读取停用词的文件到内存中hashset保存
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(STOP_WORD_PATH))) {
             while(true){
                 String line = bufferedReader.readLine();
                 if(line==null){
                     // 读取文件完毕
                     break;
                 }
                 // 保存到hashset中
                 stopwords.add(line);
             }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // 此处要加上索引对象的实例
    private Index index = new Index();

    public DocSearcher() {
        //在构造方法的时候进行加载索引
        index.load();
        loadStopWord();
    }

    // 完成整个搜索过程的方法
    // 参数（输入部分） 用户给出的查询词
    // 返回值（输出部分）返回的包装类型的搜索结果
    public List<Result> search(String query){


        //1、【分词】针对 query 查询词进行分词
        List<Term> oldTerms = ToAnalysis.parse(query).getTerms();

        List<Term> terms = new ArrayList<>();
        //1。5 针对分词结果，使用停用词表进行过滤，干掉停用词表中包含的内容
        for (Term term:oldTerms) {
            if(stopwords.contains(term.getName())){// 如果包含的话那么直接跳过
                continue;
            }
            // 如果不在暂停此表中，那么分词结果不干掉
            terms.add(term);
        }

        //2、【触发】针对分词结果进行查倒排
        List<List<Weight>> termResult = new ArrayList<>();

        for (Term term:terms) {
            String word = term.getName();
            List<Weight> invertedList = index.getInverted(word);//根据查询词进行查倒排
            //虽然倒排索引中有很多的词，但是这里的词一定是之前解析的文档中已经存在的
            // 但是如果word在倒排索引中查找不到的话
            if(invertedList==null){
                 continue;//跳过
            }
            termResult.add(invertedList);//批量追加一组元素，所以是addAll()
        }

        //2、5针对多个分词结果触发的重复文档进行权重合并
        List<Weight> allTermResult = mergeResult(termResult);

        //3、【排序】针对触发的结果按照相关程度进行降序排序
        allTermResult.sort(new Comparator<Weight>() {
            @Override
            public int compare(Weight o1, Weight o2) {
                // 重写比较器，降序排序
                return o2.getWeight()-o1.getWeight();
            }
        });

        //4、【包装结果】针对排序的结果，去查正排，构造出要返回的数据.
        List<Result> results = new ArrayList<>();
        for(Weight weight:allTermResult){
            DocInfo docInfo = index.getDocInfo(weight.getDocId());
            Result result = new Result();
            result.setTitle(docInfo.getTitle());
            result.setUrl(docInfo.getUrl());
            //描述是 正文的一段内容的摘要，得包含查询词或者查询词的一部分
            result.setDesc(GenDesc(docInfo.getContent(),terms));
            //可以获取到所有的查询词结果
            // 遍历分词结果，看那个结果在正文中出现

            // 就针对这个包含的分词结果，去正文中查找，找到对应的位置，以这个词的位置为中心
            // 往前截取60个字符，然后再以描述开始往后截取160个字符作为整个秒描述
            results.add(result);
        }

         return results;
    }

    // 通过这个内部类，来描述一个元素在二维数组中的位置
    static class Pos{
        public int row;
        public int col;

        public Pos(int row, int col) {
            this.row = row;
            this.col = col;
        }
    }

    private List<Weight> mergeResult(List<List<Weight>> source) {
        // 在进行合并的时候是把多个行合并成一行
        //合并过程中是不需要操作这个二维list，里面的每个元素的
        // 涉及到行和列

        //1、针对每一行进行排序，按照id进行升序排序
        for(List<Weight> curRow:source){
            curRow.sort(new Comparator<Weight>() {
                @Override
                public int compare(Weight o1, Weight o2) {
                    return o1.getDocId()-o2.getDocId();
                }
            });
        }

        //2、借助优先级队列对这些行进行合并
        List<Weight> target = new ArrayList<>();
        // 创建优先级队列，按照weight的 dociId 取小的更优先
        PriorityQueue<Pos> queue = new PriorityQueue<>(new Comparator<Pos>() {
            @Override
            public int compare(Pos o1, Pos o2) {
                // 现根据pos值找到对应的weight对象，再根据weight的id进行排序
                Weight w1 = source.get(o1.row).get(o1.col);
                Weight w2 = source.get(o2.row).get(o2.col);
                return w1.getDocId()-w2.getDocId();
            }
        });

        for(int row =0;row<source.size();row++){
            //初始插入的元素的列为0
            queue.offer(new Pos(row,0));
        }
        while(!queue.isEmpty()){
            Pos minPos = queue.poll();
            Weight curWeight = source.get(minPos.row).get(minPos.col);
            //看看这个取到的Weight是否与前一个插入到 target中的结果是相同的docId
            // 如果是，那么合并
            if(target.size()>0){
                //取出上此插入的最后元素
                Weight lastWeight = target.get(target.size()-1);
                if(lastWeight.getDocId()==curWeight.getDocId()){
                    // 说明遇到了相同的文档
                    lastWeight.setWeight(lastWeight.getWeight()+curWeight.getWeight());
                }else{
                    //如果文档不相同的话，那么就直接把curWeight给插入到target的末尾
                    target.add(curWeight);
                }
            }else{
                // 如果当前target 是空的，那么就直接把curWeight给插入到 target的末尾
                target.add(curWeight);
            }

            // 当前元素处理完之后，要把对应这个元素的光标往后移动，来取这一行的下一个元素
            Pos newPos = new Pos(minPos.row,minPos.col+1);
            if(newPos.col>=source.get(newPos.row).size()){
                //说明到达这一行的末尾了，说明处理完毕了
                continue;
            }
            queue.offer(newPos);
        }


        return target;
    }

    private String GenDesc(String content, List<Term> terms) {
        // 遍历分词结果，看看哪个分词结果在content结果中先出现
        // 只体现描述与分词具有相关性即可，找到第一个出现的分词即可break
        int firstPos = -1;
        for(Term term:terms){
            // 在分词库直接针对词进行转小写了，但是正文中不一定都是小写的字符
            // 必须把正文先转成小写的在进行查询位置
            String word = term.getName();
            // 此处需要 " 全字匹配 "，word独立成词才能查找出来，而不是作为词的一部分 ArrayList List 老婆 老婆饼
            // 这里的全字匹配不严谨，还需要使用正则表达式 abx list.  .... list) 并不符合全字匹配但是是符合查询条件的,indexOf不支持正则表达式
            // 转换思路，把 后面带符号的形式 转换成 空格+单词+空格
            content = content.toLowerCase().replaceAll("\\b"+word+"\\b"," "+word+" ");
            firstPos = content.toLowerCase().indexOf(" "+word+" ");// 在正文中找到这个词的位置
            if(firstPos>=0){
                // 说明这个词找到了,直接break
                break;
            }
        }

        if(firstPos==-1){
            // 所有的分词结果都不在正文中存在
            // 这是属于比较极端的情况
            // 返回一个正文的前160个字符即可
            if(content.length()>160) {
                return content.substring(0,160)+"...";
            }

            return content;
        }

        // 从firstPos作为基准位置，往前找60个字符作为起始位置
        String desc="";

        int descBeg = firstPos<60?0:firstPos-60;
        if(descBeg+160>content.length()){
            desc = content.substring(descBeg);
        }else{
            desc = content.substring(descBeg,descBeg+160)+"...";
        }

        // 在此处加上一个替换操作，把描述中和分词结果中相同的部分，给加上一层<i>标签，通过replaceAll实现
        for(Term term:terms){
            String word = term.getName();
            // 同样也是全字匹配,加上标签做上标记
            desc = desc.replaceAll("(?i) "+word+" ","<i> "+word+" </i>");
        }
        return desc;
    }

    public static void main(String[] args) {
        DocSearcher docSearcher = new DocSearcher();
        Scanner scanner = new Scanner(System.in);
        while(true){
            System.out.print("->");
            String query = scanner.next();
            List<Result> results = docSearcher.search(query);
            for (Result result:results) {
                System.out.println(result.toString());
                System.out.println("================================");
            }
        }
    }
}

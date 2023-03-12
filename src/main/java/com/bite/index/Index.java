package com.bite.index;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.bite.config.FileConfig;
import org.ansj.domain.Term;
import org.ansj.splitWord.analysis.ToAnalysis;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// 通过这个类在内存中构造出索引结构
public class Index {


    // 索引文件保存路径
    private static String INDEX_PATH = null;

    static {
        if (FileConfig.isOnline) {
            INDEX_PATH = "/root/javadoc/";
        } else {
            INDEX_PATH = "C:/Users/rain7/Desktop/";
        }
    }

    private ObjectMapper objectMapper = new ObjectMapper();

    // 使用数组下标表示 docId

    private ArrayList<DocInfo> forwardIndex = new ArrayList<>();// 正排索引的数据结构

    // 使用哈希表表示倒排索引
    // key-->词
    // value --> 词关联的文章
    private HashMap<String,ArrayList<Weight>> invertedIndex = new HashMap<>();//倒排索引的数据结构

    // 这个类要提供的方法
    //1、给定一个 docID,在正排索引当中查询文档的详细信息，通过正排索引查询文档数据
    public DocInfo getDocInfo(int docId){
        return forwardIndex.get(docId); // o1复杂度，查询高效
    }

    //2、给定一个词，在倒排索引当中，查询那些文档与这个词相关联
    // 思考这里的返回值，单纯的返回整数的list是否可行呢？不太好
    // 词和文档之间存在一定的相关性，
    public List<Weight> getInverted(String term){//通过倒排索引得到与查询词相关的一组文档Weight(只需要直到docId)
        return invertedIndex.get(term); // o1复杂度，查询高效
    }

    //3、往索引当中新增一个文档,用于parser类中parseHTML方法解析完一个html文件之后要构建索引保存到内存的数据结构中
    public void addDoc(String title,String url,String content){
        // 新增文档操作需要同时给正排索引和倒排索引新增信息，将文档的信息构建成正排、倒排保存到内存中

        // 构建正排索引
        DocInfo docInfo = buildForword(title,url,content);

        // 构建倒排索引
        buildInverted(docInfo);
    }

    // 构建倒排索引,这里只是针对一个文档进行构建而已，其实在遍历每个文件的时候都会遍历所有分词构建倒排索引
    private void buildInverted(DocInfo docInfo) {

        class WordCount{
           //表示这个词在标题中出现的次数
           public int titleCount;
           //表示这个词在正文中出现的次数
           public int contentCount;
        }

        // 用来统计分词在（标题、正文）词频的数据结构,总的出现频数
        HashMap<String,WordCount> wordCountHashMap = new HashMap<>();

        //=======================标题频数统计======================
        //1、针对文档标题进行分词
        List<Term> terms = ToAnalysis.parse(docInfo.getTitle()).getTerms();

        //2、遍历分词结果，统计出每个词出现的次数
       for(Term term:terms){
           // 先判断term是否存在
           String word = term.getName();
           WordCount wordCount = wordCountHashMap.get(word);// 查询该分词出现的次数

           // 在标题中出现0次，在正文中也出现0次
          if(wordCount==null){//如果不存在，就创建一个新的键值对，titleCount=1
              WordCount newWordCnt = new WordCount();
              newWordCnt.titleCount=1;
              newWordCnt.contentCount=0;
              wordCountHashMap.put(word,newWordCnt);
          }else{ //如果已经存在，就找到之前的值，对应的titleCount+1
              wordCount.titleCount++;
          }
       }

       //==========================正文频数统计===============================
        //3、针对正文进行分词
        terms = ToAnalysis.parse(docInfo.getContent()).getTerms();

        //4、遍历分词结果，统计每个词出现的次数
        for(Term term:terms){
            String word = term.getName();
            WordCount wordCount = wordCountHashMap.get(word);
            if(wordCount==null){
                WordCount newWordCount = new WordCount();
                newWordCount.titleCount=0;
                newWordCount.contentCount=1;
                wordCountHashMap.put(word,newWordCount);
            }else {
                wordCount.contentCount+=1;
            }
        }

        //==========================汇总分词的频数===========================================

        //5、把上面的结果汇总到一个 hashMap 当中,此时所有分词的出现频数都放到了 WordCountHashMap 当中

        // 最终文档的权重设定成 weight = 标题中出现的次数*10+ 正文中出现的次数（实际上公式很复杂，这个权重公式是拍脑门拍出来的）

        //6、遍历hashMap,依次更新倒排索引中的结构

        //======================遍历所有分词，查询倒排索引的数据库，倒排拉链插入文档信息==================================================
        for(Map.Entry<String,WordCount> entry:wordCountHashMap.entrySet()){

            synchronized (invertedIndex) {
                // 倒排拉链 (一个String的词 后面 跟着一个List   word -> docId1 docId2 docId3)
                List<Weight> invertedList = invertedIndex.get(entry.getKey());

                if(invertedList==null){//如果为null，那就插入一个新的键值对
                    ArrayList<Weight> newInvertedList = new ArrayList<>();
                    // 把新的文档（当前 docInfo）构造成 weight对象，插入进来
                    Weight weight =  new Weight();
                    weight.setDocId(docInfo.getDocId());
                    // 权重计算公式=标题次数*10+正文次数
                    weight.setWeight(entry.getValue().titleCount*10+entry.getValue().contentCount);

                    newInvertedList.add(weight);

                    invertedIndex.put(entry.getKey(),newInvertedList);

                }else{//如果非空那么就把当前文档，构造出一个weight对象，插入到倒排拉链的后面
                    Weight weight =  new Weight();
                    weight.setDocId(docInfo.getDocId());
                    // 权重计算公式=标题次数*10+正文次数
                    weight.setWeight(entry.getValue().titleCount*10+entry.getValue().contentCount);
                    invertedList.add(weight);
                }
            }
        }

    }

    // 构建正排索引
    private DocInfo buildForword(String title,String url,String content) {
        // 将解析的文档内容构造成一个类
        DocInfo docInfo = new DocInfo();
        docInfo.setTitle(title);
        docInfo.setUrl(url);
        docInfo.setContent(content);

        synchronized (forwardIndex) {
            // 新加入的docId 放在 forwordIndex 数组的最后，所以id就是数组的长度
            docInfo.setDocId(forwardIndex.size());

            // 往正排索引中插入文档数据
            forwardIndex.add(docInfo);
        }
        return docInfo;
    }

    //4、把内存当中的索引结构保存到磁盘当中
    public void save(){
        long start = System.currentTimeMillis();
        System.out.println("保存索引开始!");

        // 1、先判断索引文件保存的路径是否存在
        File indexPathFile = new File(INDEX_PATH);
        if(!indexPathFile.exists()){
            indexPathFile.mkdirs();
        }

        // 保存正排索引的信息
        File forwordIndexFile = new File(INDEX_PATH+"forword.txt");
        // 保存倒排索引的信息
        File invertedIndexFile = new File(INDEX_PATH+"inverted.txt");

        try {
           // 将内存中的索引信息进行序列化
            objectMapper.writeValue(forwordIndexFile,forwardIndex);
            objectMapper.writeValue(invertedIndexFile,invertedIndex);
        } catch (IOException e) {
            e.printStackTrace();
        }

        long end = System.currentTimeMillis();
        System.out.println("保存索引完成!");

        System.out.println("保存消耗时间为 "+(end-start)+" ms");
    }

    //5、把文件中的索引数据加载到内存当中
    public void load(){
        long start = System.currentTimeMillis();
        System.out.println("加载索引开始!");

        //1、设置加载索引的路径
        File forwordFile = new File(INDEX_PATH+"forword.txt");
        File invertedFile = new File(INDEX_PATH+"inverted.txt");

        //2、从文件中解析索引数据
        try {
            // 反序列化的时候 需要指定把文件中字符串 转换成什么类型的数据 ，TypeReference<> 通过泛型参数指定实际类型
            forwardIndex = objectMapper.readValue(forwordFile,new TypeReference<ArrayList<DocInfo>>(){});
            invertedIndex = objectMapper.readValue(invertedFile,new TypeReference<HashMap<String,ArrayList<Weight>>>(){});

        } catch (IOException e) {
            e.printStackTrace();
        }

        long end = System.currentTimeMillis();
        System.out.println("加载索引结束!");

        System.out.println("加载消耗时间为 "+(end-start)+" ms");
    }

    public static void main(String[] args) {
        Index index =new Index();
        //开始加载索引
        index.load();
        //加载索引完成


    }

}

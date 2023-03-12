package com.bite.parser;

import com.bite.index.Index;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;


public class Parser {

    // 指定加载文档的路径
    private static final String INPUT_FILE ="C:/Users/rain7/Desktop/docs/api/";

    private Index index = new Index();

    private AtomicLong t1 = new AtomicLong(0);
    private AtomicLong t2 = new AtomicLong(0);

    // 通过这个方法实现单线程制作索引
    public void run(){
        long start = System.currentTimeMillis();

        ArrayList<File> fileList = new ArrayList<File>();

        // 遍历文档路径,获取文档中所有的 HTML 文件
        File rootFile = new File(INPUT_FILE);

        long startEmnu = System.currentTimeMillis();
        emnuFile(rootFile,fileList);
        long endEmnu = System.currentTimeMillis();


        // 对 每个html文件进行内容解析
        long startFor = System.currentTimeMillis();
        for (File file:fileList) {
            System.out.println("开始解析： " +file.getName());
            parseHTML(file);
        }
        long endFor = System.currentTimeMillis();



        // 把内存中构造好的索引数据结构，保存到指定的文件当中
        index.save();

        long end = System.currentTimeMillis();

        System.out.println("枚举文件消耗时间："+(endEmnu-startEmnu)+" ms");
        System.out.println("遍历文件进行解析时间："+(endFor-startFor)+" ms");
        System.out.println("索引总共需要的制作时间： "+(end-start)+" ms");

    }

    //通过这个方法实现多线程制作索引，通过打印时间发现遍历文件进行解析 消耗的时间非常多，需要多线程遍历解析
    public void runByThread(){
        long begin = System.currentTimeMillis();

        ArrayList<File> fileList = new ArrayList<>();

        // 遍历文档路径,获取文档中所有的 HTML 文件
        File rootFile = new File(INPUT_FILE);
        emnuFile(rootFile,fileList);

        CountDownLatch latch = new CountDownLatch(fileList.size());
        // 2、此处为了实现多线程遍历文件制作索引，就直接引入线程池实现索引
        ExecutorService executorService = Executors.newFixedThreadPool(8 );
        for(File file:fileList){
            executorService.submit(new Runnable(){
                @Override
                public void run() {
//                    System.out.println("开始解析:"+file.getAbsolutePath());
                    parseHTML(file);
                    latch.countDown();
                }
            });


        }

        try {
            // 等待所有线程全部完成任务
            latch.await();//await方法会阻塞，直到所有的选手都调用 countDown 撞线完毕后，才能阻塞结束
            executorService.shutdown();//干掉线程池中的线程,解决 main方法已经结束，进程还未结束的问题
            // 线程池的线程不是后台线程，main方法执行完了还在等待新任务的到来，所以总进程无法结束，需要我们手动结束线程池里面的线程
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        // 把内存中构造好的索引数据结构，保存到指定的文件当中
        index.save();

        long end = System.currentTimeMillis();
        System.out.println("多线程的时间："+(end-begin)+" ms");


        System.out.println("t1解析内容的总时间:"+t1+"   t2制作索引的总时间:"+t2);
    }

    /**
     * 解析 HTML 文件
     * @param file
     * @return
     */



    private void parseHTML(File file) {
        // 解析html文件需要 解析正文 以及 标题、描述（正文的一段摘要）、url获取到
        // 要想得到描述必须拿到正文

        //1、解析HTML的标题
        String title = parseTitle(file);

        //2、解析HTML对应的URL
        String url = parseUrl(file);

        long beg = System.nanoTime();
        //3、解析出HTML 对应的正文
        String content = parseContentByRegex(file);
        long mid = System.nanoTime();

        // 只有这一步是写操作，所以要保证线程安全，加锁synchronized
        //4、把解析后的数据添加到索引当中
        index.addDoc(title,url,content);
        long end = System.nanoTime();

        // parseHTML 会循环调用很多次，单次调用时间较短
        t1.addAndGet(mid-beg);
        t2.addAndGet(end-mid);
    }

    /**
     * 解析html文件的正文,读取<div><div/>中包括的内容
     * @return
     */
    public  String parseContent(File file) {
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(file),1024*1024)) { // 文件缓冲区
            // 是否要拷贝的开关
            boolean isCopy = true;
            StringBuilder content = new StringBuilder();
            while(true){
                int ret =  bufferedReader.read();
                if(ret==-1){
                    break;
                }
                //如果不是-1，那么就是一个合法的字符
                char c = (char)ret;
                // 对字符进行识别，判断拷贝开关是否开启关闭
                if(isCopy){ // 如果拷贝开关为true，进入条件中
                    if(c=='<'){//如果碰到<,那么关闭开关
                        isCopy=false;
                        continue;
                    }
                    if(c=='\r' || c=='\n'){// 经过测试查看，发现原文中有很多换行符，所以去除，方便后续截取摘要
                        c=' ';
                    }
                    // 如果不是左括号，同时开关是开的，那么拷贝字符
                    content.append(c);
                }else{ //如果拷贝开关为false,那么跳过不拷贝
                    //如果字符为>,那么拷贝开关打开
                    if(c=='>'){
                        isCopy=true;
                    }
                }
            }
            return content.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    // 这个方法内部基于正则表达式，实现去标签，以及去除 <script>
    public String parseContentByRegex(File file){
        // 把整个文件的内容读取到 content中
        String content  = readFile(file);
        //  把<srcipt>标签去了
        content = content.replaceAll("<script.*?>(.*?)</script>"," ");
        // 把 普通标签<> 替换掉
        content = content.replaceAll("<.*?>"," ");
        // 因为上面把标签都替换成空格了，所以会发生中间有很多空格的情况，需要合并多个空格
        content = content.replaceAll("\\s+"," ");
        // 顺序不能错，否则发生错误
        return content;
    }

    private String readFile(File f){
        try(BufferedReader bufferedReader = new BufferedReader(new FileReader(f))){
           StringBuilder content = new StringBuilder();
            while(true){
                int ret = bufferedReader.read();
                if(ret==-1){
                    break;
                }
                char c = (char)ret;
                if(c=='\n'|| c=='\r'){ // 把换行去了
                    c=' ';
                }
                content.append(c);
            }
            return content.toString();
        }catch (IOException e){
            e.printStackTrace();
        }

        return "";
    }

    /**
     * 解析html文件的url
     * @return
     */
    private String parseUrl(File file) {
        // 这里展示的url，我们希望能够跳转到线上java文档的地址
        // 所以展示的是 java线上文档的url

        // 线上文档的url   https://docs.oracle.com/javase/8/docs/api/
        // 本地文档的url        C:\Users\rain7\Desktop\docs\api\java\awt\color\CMMException.html

        // 线上文档的前半部分 ulr
        String part1 ="https://docs.oracle.com/javase/8/docs/api/";

        // 截取本地文档中 除前半部分的固定url 的后半内容
        String part2 = file.getAbsolutePath().substring(INPUT_FILE.length());
        // part2中的反斜杠全部替换成正斜杠，其实浏览器自身的容错能力也支持 反斜杠、正斜杠识别
        part2 = part2.replaceAll("\\\\","/");

        return part1+part2;
    }

//    public static void main(String[] args) {
//        Parser parser = new Parser();
//        Index index = new Index();
//        index.load();
//        System.out.println("索引加载完成!");
//    }

    /**
     * 解析html文件的标题
     * @return 返回搜索结果的标题
     */
    private String parseTitle(File file) {
        // 通过查看html文件的源码，发现title 标签的内容就是文件名

        int index = file.getName().lastIndexOf('.');
        return file.getName().substring(0,index);
    }



    /**
     * 遍历根目录文件下的所有非 HTML文件，保存文件到 list集合中
     * @param rootFile 传入的根目录文件，
     * @param fileList 符合条件的文件保存到该集合中
     */

    private void  emnuFile(File rootFile, ArrayList<File> fileList) {
        // 要通过递归操作，遍历跟路径中的所有普通文件
        File[] files = rootFile.listFiles(); // 显示当前文件对象下的所有文件，整合成文件对象放到数组中，只显示一级

        for (File file:files) {
            if(file.isDirectory()){ // 如果文件是目录的话，继续递归下去
                emnuFile(file,fileList);
            }else{ // 如果不是目录，只是普通的文件

                // 排除枚举的所有文件中的 非HTML文件
                if(file.getAbsolutePath().endsWith(".html")){
                    fileList.add(file);
                }
            }
        }
    }


    public static void main(String[] args) {
        Parser parser = new Parser();
        System.out.println("多线程开始制作索引!");
        parser.runByThread();
        System.out.println("多线程制作索引完成!");
    }
}

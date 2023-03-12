package com.bite.index;// 这个类是把 文档id 与 文档与词的相关性的 权重 进行一个包裹

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Weight {// 与构建倒排索引数据结构相关的类
    private int docId;
    // 表示词 与文档之间的相关性
    // 值越大，就认为相关性越强，具体计算
    private int weight;

}

package com.bite.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DocInfo {// 与构建正排索引有关的类
    private int docId;
    private String title;
    private String url;
    private String content;
}

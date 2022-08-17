package com.bite.search;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 这个类是来保存搜索结果的集合
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Result {
    private String title;
    private String url;
    private String desc;// 描述是 正文的一段摘要
}

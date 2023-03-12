package com.bite.api;

import com.bite.search.DocSearcher;
import com.bite.search.Result;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


import java.util.List;

@RestController
public class DocSearchController {

   private static DocSearcher docSearcher = new DocSearcher();

   @Autowired
   private ObjectMapper objectMapper;

    @RequestMapping(value = "/searcher",produces = "application/json;charset=utf-8")
    public String getResult(@RequestParam("query") String query) throws JsonProcessingException {
        if(query==null || query.equals("")){
            return "";
        }

        List<Result> list = docSearcher.search(query);
        return objectMapper.writeValueAsString(list);
    }

}

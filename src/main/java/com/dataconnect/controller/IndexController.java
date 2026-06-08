package com.dataconnect.controller;

import com.dataconnect.repository.DsConfigRepository;
import com.dataconnect.repository.FlowConfigRepository;
import com.dataconnect.repository.TaskConfigRepository;
import com.dataconnect.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    @Autowired
    private DsConfigRepository dsConfigRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    @Autowired
    private TaskConfigRepository taskConfigRepository;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("dsCount", dsConfigRepository.count());
        model.addAttribute("templateCount", templateRepository.findByIsDeleted(0).size());
        model.addAttribute("flowCount", flowConfigRepository.count());
        model.addAttribute("runningTaskCount", taskConfigRepository.findByStatus("RUNNING").size());
        return "index";
    }
}

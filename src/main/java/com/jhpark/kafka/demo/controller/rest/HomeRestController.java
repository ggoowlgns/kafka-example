package com.jhpark.kafka.demo.controller.rest;

import org.springframework.web.bind.annotation.Mapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping(path = "/api")
public class HomeRestController {

  @RequestMapping(path = "/home")
  public String home(HttpServletResponse response) {
    return "OK";
  }

}

package com.xxl.job.admin.controller;

import com.xxl.job.admin.service.LoginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import javax.servlet.http.Cookie;

import java.util.concurrent.TimeUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

public class JobInfoControllerTest extends AbstractSpringMvcTest {

  private Cookie cookie;

  @BeforeEach
  public void login() throws Exception {
    MvcResult ret = mockMvc.perform(
        post("/login")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .param("userName", "admin")
            .param("password", "123456")
    ).andReturn();
    cookie = ret.getResponse().getCookie(LoginService.LOGIN_IDENTITY_KEY);
  }

  @Test
  public void testAdd() throws Exception {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<String, String>();
    parameters.add("jobGroup", "1");
    parameters.add("triggerStatus", "-1");

    MvcResult ret = mockMvc.perform(
        post("/jobinfo/pageList")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            //.content(paramsJson)
            .params(parameters)
            .cookie(cookie)
    ).andReturn();

    System.out.println(ret.getResponse().getContentAsString());
  }

  @Test
  public void testBatchJob() throws Exception {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("id", "2");

    for (int i=0; i<10; i++) {
      Thread t = new Thread(() -> {
        try {
          MvcResult ret = mockMvc.perform(
                  post("/jobinfo/trigger")
                          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                          //.content(paramsJson)
                          .params(parameters)
                          .cookie(cookie)
          ).andReturn();

          System.out.println(ret.getResponse().getContentAsString());
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
      t.start();
    }
    System.out.println("--- over ---");

    TimeUnit.SECONDS.sleep(30);
  }

  @Test
  public void testBatchJob2() throws Exception {
    MultiValueMap<String, String> parameters = new LinkedMultiValueMap<>();
    parameters.add("id", "2");

    Thread[] tList = new Thread[5];
    for (int i=0; i<5; i++) {
      Thread t = new Thread(() -> {
        try {
          MvcResult ret = mockMvc.perform(
                  post("/jobinfo/trigger")
                          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                          //.content(paramsJson)
                          .params(parameters)
                          .cookie(cookie)
          ).andReturn();

          System.out.println(ret.getResponse().getContentAsString());
        } catch (Exception e) {
          e.printStackTrace();
        }
      });
      tList[i] = t;
    }

    for (Thread t : tList) {
      t.start();
    }

    System.out.println("--- over ---");

    TimeUnit.SECONDS.sleep(30);
  }

}

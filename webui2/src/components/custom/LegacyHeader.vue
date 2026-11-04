<script setup lang="ts">
import { useRoute } from 'vue-router';

const route = useRoute();

const isCurrent = (path: string) => route.path === path;

const alertContactAdmin = () => {
  window.alert('我们没有充值功能, 仅支持捐赠. 但这不会给您带来任何游戏内的特权或优势. 如您有其他需要或帮助, 请在QQ群内联系管理员:866626769.');
};

const alertNotOpen = () => {
  window.alert('未开放');
};
</script>

<template>
  <!-- Main Header -->
  <header class="main-header">
    <!-- Header Top -->
    <div class="header-top">
      <div class="auto-container clearfix">
        <div class="top-left clearfix">
          <ul class="info-list">
            <li>勇士，你来啦！</li>
          </ul>
        </div>
      </div>
    </div>

    <!-- Header Upper -->
    <div class="header-upper">
      <div class="inner-container">
        <div class="auto-container clearfix">
          <!--Logo-->
          <div class="logo-outer">
            <div class="logo">
              <a href="/home"><img src="/assets/legacy/images/d_logo.png" alt="" title="Game" style="width: 300px;"/></a>
            </div>
          </div>

          <!--Nav Box-->
          <div class="nav-outer clearfix">
            <!--Mobile Navigation Toggler-->
            <div class="mobile-nav-toggler"><span class="icon flaticon-menu-1"></span></div>

            <!-- Main Menu -->
            <nav class="main-menu navbar-expand-md navbar-light">
              <div class="collapse navbar-collapse show clearfix" id="navbarSupportedContent">
                <ul class="navigation clearfix pull-left">
                  <li :class="[{ current: isCurrent('/home') }, 'dropdown']">
                    <a href="/home">主页</a>
                  </li>

                  <li class="dropdown">
                    <a href="#" @click.prevent>游戏资源</a>
                    <ul>
                      <li><a target="_blank" href="https://github.com/onlyGuo/DNFLogin">登录器源码</a></li>
                      <li><a target="_blank" href="https://github.com/onlyGuo/dnf-server-public">后台源码</a></li>
                    </ul>
                  </li>

                  <li>
                    <a href="/notice">公告</a>
                  </li>
                </ul>

                <ul class="navigation pull-right clearfix">
                  <li class="dropdown">
                    <a href="#" @click.prevent>账号服务</a>
                    <ul>
                      <li><a href="#" @click.prevent="alertContactAdmin" target="_blank" rel="noreferrer">GM申请(暂未开放)</a></li>
                      <li><a href="/login" target="_blank" rel="noreferrer">GM后台</a></li>
                      <li><a href="#" @click.prevent="alertContactAdmin">账号充值(不开放)</a></li>
                    </ul>
                  </li>

                  <li class="dropdown">
                    <a href="#" @click.prevent="alertNotOpen">社区</a>
                  </li>

                  <li>
                    <a href="#" @click.prevent="alertContactAdmin">客服</a>
                  </li>
                </ul>
              </div>
            </nav>
            <!-- Main Menu End-->
          </div>
        </div>
      </div>
    </div>
    <!--End Header Upper-->

    <!-- Sticky Header  -->
    <div class="sticky-header">
      <div class="auto-container clearfix">
        <!--Logo-->
        <div class="logo pull-left">
          <a href="/home" title=""><img src="/assets/legacy/images/d_logo2.png" style="width: 200px;" alt="" title="" /></a>
        </div>
        <!--Right Col-->
        <div class="pull-right">
          <!-- Main Menu -->
          <nav class="main-menu clearfix">
            <!--Keep This Empty / Menu will come through Javascript-->
          </nav>
          <!-- Main Menu End-->
        </div>
      </div>
    </div>
    <!-- End Sticky Menu -->

    <!-- Mobile Menu  -->
    <div class="mobile-menu">
      <div class="menu-backdrop"></div>
      <div class="close-btn"><span class="icon flaticon-close"></span></div>

      <nav class="menu-box">
        <div class="nav-logo">
          <a href="/home"><img src="/assets/legacy/images/logo.png" alt="" title="" /></a>
        </div>
        <div class="menu-outer">
          <!--Here Menu Will Come Automatically Via Javascript / Same Menu as in Header-->
        </div>
        <!--Social Links-->
        <div class="social-links">
          <ul class="clearfix">
            <li>
              <a href="#" @click.prevent><span class="fab fa-twitter"></span></a>
            </li>
            <li>
              <a href="#" @click.prevent><span class="fab fa-facebook-square"></span></a>
            </li>
            <li>
              <a href="#" @click.prevent><span class="fab fa-pinterest-p"></span></a>
            </li>
            <li>
              <a href="#" @click.prevent><span class="fab fa-instagram"></span></a>
            </li>
            <li>
              <a href="#" @click.prevent><span class="fab fa-youtube"></span></a>
            </li>
          </ul>
        </div>
      </nav>
    </div>
    <!-- End Mobile Menu -->
  </header>
  <!-- End Main Header -->
</template>

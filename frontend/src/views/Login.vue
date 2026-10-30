<template>
  <div class="login-page">
    <el-card class="login-card">
      <div class="title">公司基本信息管理系统</div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password
            autocomplete="current-password" @keyup.enter="onSubmit" />
        </el-form-item>
        <el-button type="primary" class="login-btn" :loading="loading" @click="onSubmit">
          登 录
        </el-button>
      </el-form>
      <el-alert type="info" :closable="false" class="tip">
        <p>演示账号：</p>
        <p>admin / Admin@123（管理员）</p>
        <p>hr / Hr@123456（人事）　pm / Pm@123456（项目经理）</p>
        <p>zhangsan / Emp@123456（普通员工）</p>
      </el-alert>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function onSubmit() {
  await formRef.value?.validate()
  loading.value = true
  try {
    await auth.login(form.username, form.password)
    ElMessage.success('登录成功')
    const redirect = (route.query.redirect as string) || '/'
    router.push(redirect)
  } catch {
    // 错误信息已由拦截器提示
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f3b6e 0%, #2c5aa0 100%);
}

.login-card {
  width: 400px;
}

.title {
  font-size: 20px;
  font-weight: 600;
  text-align: center;
  margin-bottom: 24px;
}

.login-btn {
  width: 100%;
}

.tip {
  margin-top: 16px;
}

.tip p {
  margin: 2px 0;
  font-size: 12px;
}
</style>

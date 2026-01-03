import { defineStore } from 'pinia';
import { ref } from 'vue';

export interface UserInfo {
  id?: string;
  username?: string;
  avatar?: string;
  [key: string]: any;
}

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo>({
    username: 'User',
    avatar: '',
  });

  function setUserInfo(info: UserInfo) {
    userInfo.value = { ...userInfo.value, ...info };
  }

  function clearUserInfo() {
    userInfo.value = {
      username: 'User',
      avatar: '',
    };
  }

  return {
    userInfo,
    setUserInfo,
    clearUserInfo,
  };
});


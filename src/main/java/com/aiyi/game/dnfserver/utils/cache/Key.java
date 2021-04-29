package com.aiyi.game.dnfserver.utils.cache;

/**
 * 缓存Key
 */
public class Key {

    private final StringBuffer key = new StringBuffer();

    /**
     * 构建缓存键对象
     * @param key
     *      缓存键对象
     * @return
     */
    public static Key as(String ...key){
        Key keyEntity = new Key();
        for (int i = 0; i < key.length; i++){
            keyEntity.key.append(key[i]);
            if (i < key.length - 1){
                keyEntity.key.append(":");
            }
        }
        return keyEntity;
    }

    /**
     * 在键中追加信息
     * @param key
     *      预追加的信息
     * @return
     */
    public Key link(String key){
        this.key.append(":").append(key);
        return this;
    }

    @Override
    public String toString() {
        return key.toString();
    }
}

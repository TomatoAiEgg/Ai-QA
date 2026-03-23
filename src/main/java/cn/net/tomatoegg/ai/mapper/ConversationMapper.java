package cn.net.tomatoegg.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import cn.net.tomatoegg.ai.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {
}

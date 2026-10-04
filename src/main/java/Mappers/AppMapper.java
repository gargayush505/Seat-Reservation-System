package Mappers;

import jakarta.validation.Valid;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AppMapper {

	void createShow(String request);

}

package Dao;

import org.springframework.beans.factory.annotation.Autowired;

import Mappers.AppMapper;
import Models.ShowVO;
import jakarta.validation.Valid;

public class AppDao {
	@Autowired
	AppMapper appMapper;

	public ShowVO createShow(@Valid String request) {
		appMapper.createShow(request);
		return null;
		
	}

}

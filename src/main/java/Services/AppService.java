package Services;

import org.springframework.beans.factory.annotation.Autowired;

import Dao.AppDao;
import Models.ShowVO;
import jakarta.validation.Valid;

public class AppService {

	@Autowired
	AppDao appDao;
	
	public ShowVO createShow(@Valid String request) {
		
		appDao.createShow(request);
		return null;
	}

}

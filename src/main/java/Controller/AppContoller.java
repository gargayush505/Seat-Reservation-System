package Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import Models.ShowVO;
import Services.AppService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/shows")
public class AppContoller {

	@Autowired
	AppService appService;
	
	@PostMapping
	public ResponseEntity<ShowVO> createShow(@Valid @RequestBody String request){
		
		appService.createShow(request);
		ShowVO response = appService.createShow(request);
		
		return ResponseEntity.ok(response);
	}
}

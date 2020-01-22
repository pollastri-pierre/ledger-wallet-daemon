import http from "k6/http";
import { check } from "k6";

const requests = JSON.parse(open("./requests.json"));


export let options = {
	// simulate rampup of traffic from 1 to 20 users over 5 minutes.
	stages: [
		{ duration: "5m", target: 5 },
	]
};

export default function() {

	let baseUrl = "http://localhost:8888"
	var reqs = new Array();

	for(var i=0; i<requests.length ; i++) {
		let params = {
			headers: {"pubKey": requests[i][3]},
			timeout: 30000
		};
		let req = {
			method: requests[i][0],
			url: baseUrl + requests[i][1],
			body: requests[i][2],
			params: params
		};
		// console.log("Request is : "+JSON.stringify(req,2))
		reqs.push(req);
	}
	let responses = http.batch(reqs, { timeout: 30000 });
	for (let i = 0; i < Object.keys(responses).length ; i++) {
                check(responses[i], { ["Route [" + requests[i][0] +"] "+requests[i][1]]: (r) => r.status === 200});
                check(responses[i], { ["Route [" + requests[i][0] +" < 300 ms ] "+requests[i][1]]: (r) => r.timings.duration < 300});
                check(responses[i], { ["Route [" + requests[i][0] +" < 2 sec ] "+requests[i][1]]: (r) => r.timings.duration < 2000});
                check(responses[i], { ["Route [" + requests[i][0] +" < 5 sec ] "+requests[i][1]]: (r) => r.timings.duration < 5000});
	}
};


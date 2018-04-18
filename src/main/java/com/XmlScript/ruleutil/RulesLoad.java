package com.XmlScript.ruleutil;

import com.XmlScript.rulebean.RuleBean;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

public class RulesLoad {
	private final static String fileSeparator = System.getProperty("file.separator");
	private static Set<String> imports;
	private static List<RuleBean> ruleBeans;
	private static ApplicationContext applicationContext;
	private static ServletRequest request;
	private static ServletResponse response;
	public static void setApplicationContext(ApplicationContext applicationContext) {
		RulesLoad.applicationContext = applicationContext;
	}
	public static void setRequest(ServletRequest request) {
		RulesLoad.request = request;
	}
	public static void setResponse(ServletResponse response) {
		RulesLoad.response = response;
	}
	public static List<RuleBean> getRuleBeans() {
		return ruleBeans;
	}
	public static void Load(String filePath,String rulename) throws Exception{
		DocumentBuilderFactory domfac = DocumentBuilderFactory.newInstance();
		DocumentBuilder dombuilder = domfac.newDocumentBuilder();
		InputStream is = RulesLoad.class.getResourceAsStream(filePath);
//		System.out.println(RulesLoad.class.getResource(filePath).getPath());
		Document doc = dombuilder.parse(is);
		Element root = doc.getDocumentElement();
		NodeList items = root.getChildNodes();
		if(items!=null){
			ruleBeans = new ArrayList<RuleBean>();
			imports = new HashSet<String>();
			LoadXML(RulesLoad.class.getResource(filePath),items,rulename);
		}
	}
	private static void LoadXML(URL filePathURL,NodeList items,String rulename) throws Exception{
		for (int i = 0; i < items.getLength(); i++) {
			Node item = items.item(i);
			if ("import".equals(item.getNodeName())) {
				String importStrings = null;
				for (int index = 0; index < item.getAttributes().getLength(); index++) {
					Node node = item.getAttributes().item(index);
					if("class".equals(node.getNodeName())){
						importStrings = node.getNodeValue();
					}
				}
				if(importStrings==null){
					for (int index = 0; index < item.getChildNodes().getLength(); index++) {
						Node node = item.getChildNodes().item(index);
						if("class".equals(node.getNodeName())){
							importStrings = node.getTextContent();
						}
					}
				}
				String[] importArray = importStrings.split(";");
				for (String importStr : importArray) {
					imports.add(importStr.trim());
				}
			}else if("bean".equals(item.getNodeName())){
				parserBean(item,null);
			}
		}
		for (int i = 0; i < items.getLength(); i++) {
			Node item = items.item(i);
			if("rules".equals(item.getNodeName())&&rulename.equals(item.getAttributes().getNamedItem("id").getNodeValue())){
				NodeList rules = item.getChildNodes();
				for (int j = 0; j < rules.getLength(); j++) {
					Node rule = rules.item(j);
					if("if".equals(rule.getNodeName())){
						parserIf(rule,null);
					}else if("result".equals(rule.getNodeName())){
						parserResult(rule,null);
					}else if("bean".equals(rule.getNodeName())){
						parserBean(rule,null);
					}else if("for".equals(rule.getNodeName())){
						parserFor(rule,null);
					}
				}
			}else if("include".equals(item.getNodeName())){
				String filePath = item.getAttributes().getNamedItem("file").getNodeValue();
				DocumentBuilderFactory domfac = DocumentBuilderFactory.newInstance();
				DocumentBuilder dombuilder = domfac.newDocumentBuilder();
				File file = new File(new File(filePathURL.toURI()).getParent()+fileSeparator+filePath);
				FileInputStream fileInputStream = new FileInputStream(file);
				Document doc = dombuilder.parse(fileInputStream);
				Element root = doc.getDocumentElement();
				NodeList nodeList = root.getChildNodes();
				if(nodeList!=null){
					LoadXML(file.toURI().toURL(),nodeList,rulename);
				}
			}
		}
	}
	private static void parserFor(Node item,Map<String, Object> map) throws Exception {
		String child = item.getAttributes().getNamedItem("child").getNodeValue();
		String evl = item.getAttributes().getNamedItem("value").getNodeValue();
		Object fatherObject = execute(evl, map);
		if(fatherObject != null){
			if(map == null){
				map = new HashMap<String, Object>();
			}
			if(!fatherObject.getClass().isArray()){
				if(Collection.class.isAssignableFrom(fatherObject.getClass())){
					Collection<?> collection = (Collection<?>) fatherObject;
					fatherObject = collection.toArray();
				}else{
					throw new Exception("非数组非集合对象！"+evl);
				}
			}
			int length = Array.getLength(fatherObject);
			for (int i = 0; i < length; i++) {
				Object childObject = Array.get(fatherObject, i);
				map.put(child, childObject);
				NodeList nodeList = item.getChildNodes();
				for (int j = 0; j < nodeList.getLength(); j++) {
					Node rule = nodeList.item(j);
					if("if".equals(rule.getNodeName())){
						parserIf(rule,map);
					}else if("result".equals(rule.getNodeName())){
						parserResult(rule,map);
					}else if("bean".equals(rule.getNodeName())){
						parserBean(rule,map);
					}else if("for".equals(rule.getNodeName())){
						parserFor(rule,map);
					}
				}
			}
		}else{
			throw new Exception("无对象！"+evl);
		}
	}
	private static void parserBean(Node item,Map<String, Object> map) throws Exception{
		RuleBean ruleBean = new RuleBean();
		String id = item.getAttributes().getNamedItem("id").getNodeValue();
		String type = null;
		String evl = null;
		for (int i = 0; i < item.getAttributes().getLength(); i++) {
			Node node = item.getAttributes().item(i);
			if("value".equals(node.getNodeName())){
				evl = node.getNodeValue();
			}else if("type".equals(node.getNodeName())){
				type = node.getNodeValue();
			}
		}
		if(evl==null){
			for (int i = 0; i < item.getChildNodes().getLength(); i++) {
				Node node = item.getChildNodes().item(i);
				if("value".equals(node.getNodeName())){
					evl = node.getTextContent();
				}
			}
		}
		if(evl==null){
			throw new Exception("未知的表达式！");
		}
		if(type==null){
			throw new Exception("未知的对象类型！");
		}
		Object value = null;
		if("object".equals(type)){
			value = execute(evl,map);
		}else if("function".equals(type)){
			value = null;
		}else if("string".equals(type)){
			value = new String(evl);
		}else if("byte".equals(type)){
			value = Byte.parseByte(evl);
		}else if("short".equals(type)){
			value = Short.parseShort(evl);
		}else if("int".equals(type)){
			value = Integer.parseInt(evl);
		}else if("char".equals(type)){
			char[] chars = evl.toCharArray();
			value = chars[0];
		}else if("long".equals(type)){
			value = Long.parseLong(evl);
		}else if("boolean".equals(type)){
			value = Boolean.parseBoolean(evl);
		}else if("float".equals(type)){
			value = Float.parseFloat(evl);
		}else if("double".equals(type)){
			value = Double.parseDouble(evl);
		}else if("method".equals(type)){
			value=null;
		}
		ruleBean.setBeanName(id);
		ruleBean.setBeanType(type);
		ruleBean.setBeanValue(evl);
		ruleBean.setValue(value);
		for (int i = 0; i < ruleBeans.size(); i++) {
			if(id.equals(ruleBeans.get(i).getBeanName())){
				RuleBean rule = ruleBeans.get(i);
				ruleBeans.remove(rule);
			}
		}
		ruleBeans.add(ruleBean);
	}
	private static void parserIf(Node rule,Map<String, Object> map) throws Exception{
//		ScriptEngineManager sem = new ScriptEngineManager();
//		ScriptEngine se = sem.getEngineByName("javascript");
//		StringBuffer evlBuffer = new StringBuffer();
		String evlFunctionA = rule.getAttributes().getNamedItem("evl").getNodeValue();
//		String[] evlFunctions = evlFunctionA.split("((&&)|(\\|\\|))");
//		String[] evlSign = new String[evlFunctions.length-1];
//		Pattern pattern = Pattern.compile("((&&)|(\\|\\|))");
//		Matcher matcher = pattern.matcher(evlFunctionA);
//		int evlSignIndex = 0;
//		while(matcher.find()){
//			evlSign[evlSignIndex] = matcher.group();
//			evlSignIndex++;
//		}
//		for (int index = 0; index < evlFunctions.length; index++) {
//			String[] evls = evlFunctions[index].split("(>|<|(==)|(>=)|(<=)|(!=))");
//			String[] sign = new String[evls.length-1];
//			Pattern p = Pattern.compile("(>|<|(==)|(>=)|(<=)|(!=))");
//			Matcher m = p.matcher(evlFunctions[index]);
//			int count=0;
//			while(m.find()){
//				sign[count] = m.group();
//				count++;
//			}
//			for (int k = 0; k < evls.length; k++) {
//				StringBuffer argName = new StringBuffer("arg").append(index).append(k);
//				if(evls[k].startsWith("${")){
////					evlBuffer.append("\"").append(execute(evls[k],map).toString()).append("\"");
//					se.put(argName.toString(),execute(evls[k],map));
//					evlBuffer.append(argName.toString());
//				}else{
////					evlBuffer.append("\"").append(evls[k]).append("\"");
//					se.put(argName.toString(),evls[k]);
//					evlBuffer.append(argName.toString());
//				}
//				if(k<sign.length){
//					evlBuffer.append(sign[k]);
//				}
//			}
//			if(index<evlSign.length){
//				evlBuffer.append(evlSign[index]);
//			}
//		}
//		System.out.println(evlBuffer.toString());
		Boolean value = (Boolean)execute(evlFunctionA, map);
		if(value){
			for (int k = 0; k < rule.getChildNodes().getLength(); k++) {
				if("true".equals(rule.getChildNodes().item(k).getNodeName())){
					NodeList results = rule.getChildNodes().item(k).getChildNodes();
					for (int l = 0; l < results.getLength(); l++) {
						if("result".equals(results.item(l).getNodeName())){
							parserResult(results.item(l),map);
						}else if("if".equals(results.item(l).getNodeName())){
							parserIf(results.item(l),map);
						}else if("bean".equals(results.item(l).getNodeName())){
							parserBean(results.item(l),map);
						}else if("for".equals(results.item(l).getNodeName())){
							parserFor(results.item(l),map);
						}
					}
				}
			}
		}else{
			for (int k = 0; k < rule.getChildNodes().getLength(); k++) {
				if("false".equals(rule.getChildNodes().item(k).getNodeName())){
					NodeList results = rule.getChildNodes().item(k).getChildNodes();
					for (int l = 0; l < results.getLength(); l++) {
						if("result".equals(results.item(l).getNodeName())){
							parserResult(results.item(l),map);
						}else if("if".equals(results.item(l).getNodeName())){
							parserIf(results.item(l),map);
						}else if("bean".equals(results.item(l).getNodeName())){
							parserBean(results.item(l),map);
						}else if("for".equals(results.item(l).getNodeName())){
							parserFor(results.item(l),map);
						}
					}
				}
			}
		}
	}
	private static void parserResult(Node item,Map<String, Object> map) throws Exception{
		String resultEvl = null;
		String resultType = null;
		for (int i = 0; i < item.getAttributes().getLength(); i++) {
			Node node = item.getAttributes().item(i);
			if("value".equals(node.getNodeName())){
				resultEvl = node.getNodeValue();
			}else if("type".equals(node.getNodeName())){
				resultType = node.getNodeValue();
			}
		}
		if(resultEvl==null){
			for (int i = 0; i < item.getChildNodes().getLength(); i++) {
				Node node = item.getChildNodes().item(i);
				if("value".equals(node.getNodeName())){
					resultEvl = node.getTextContent();
				}
			}
		}
		if(resultEvl==null){
			throw new Exception("未知的表达式！");
		}
		if(resultType==null){
			throw new Exception("未知的操作方式！");
		}
		if("execute".equals(resultType)){
//			if(resultEvl.startsWith("${")){
			execute(resultEvl,map);
//			}
		}else if("dispatcher".equals(resultType)){
			if(RulesLoad.request==null){
				throw new Exception("无request，无法转发！");
			}else{
				if(resultEvl.startsWith("${")){
					Object object = execute(resultEvl,map);
					if(object==null){
						throw new Exception(resultEvl+"无对象！");
					}else{
						RulesLoad.request.getRequestDispatcher(object.toString()).forward(RulesLoad.request, RulesLoad.response);
					}
				}else{
					RulesLoad.request.getRequestDispatcher(resultEvl).forward(RulesLoad.request, RulesLoad.response);
				}
			}
		}else if("redirect".equals(resultType)){
			if(RulesLoad.response==null){
				throw new Exception("无response，无法重定向！");
			}else{
				HttpServletResponse httpServletResponse = (HttpServletResponse) RulesLoad.response;
				if(resultEvl.startsWith("${")){
					Object object = execute(resultEvl,map);
					if(object==null){
						throw new Exception(resultEvl+"无对象！");
					}else{
						httpServletResponse.sendRedirect(object.toString());
					}
				}else{
					httpServletResponse.sendRedirect(resultEvl);
				}
			}
		}
	}
	public static Object execute(String evl) throws Exception{
		return execute(evl, null);
	}
	private static Object execute(String evl,Map<String, Object> map) throws Exception{
		List<Object> objects = new ArrayList<Object>();
		List<String> scriptList = getScript(evl);
		for (String script : scriptList) {
			if(script.startsWith("#[")){
				if(!script.endsWith("]")){
					throw new Exception("表达式有误！"+script);
				}
				ScriptEngineManager sem = new ScriptEngineManager();
				ScriptEngine se = sem.getEngineByName("javascript");
				StringBuffer jsEvlBuffer = new StringBuffer();
				int i=0;
				List<String> $_args = get$List(script);
				for (int index = 0; index < $_args.size(); index++) {
					String $_arg = $_args.get(index);
					if(index==0||index == $_args.size() - 1){
						if(index==0){
							$_arg = $_arg.substring(2);
						}
						if(index == $_args.size() - 1){
							$_arg = $_arg.substring(0,$_arg.length()-1);
						}
					}
					if($_arg.startsWith("${")){
						jsEvlBuffer.append("$_arg"+i);
						se.put("$_arg"+i, execute$($_arg,map));
						i++;
					}else{
						jsEvlBuffer.append($_arg);
					}
				}
//				System.out.println(jsEvlBuffer.toString());
				objects.add(se.eval(jsEvlBuffer.toString()));
			}else if(script.startsWith("${")){
				objects.add(execute$(script, map));
			}else{
//				List<String> $_args = get$List(script);
//				for (String $_arg : $_args) {
//					if($_arg.startsWith("${")){
//						objects.add(execute$($_arg,map));
//					}else{
				objects.add(script);
//					}
//				}
			}
		}
		if(objects.size()>1){
			StringBuffer sb = new StringBuffer();
			for (Object object : objects) {
				sb.append(object);
			}
			return sb.toString();
		}else if(objects.size()==1){
			return objects.get(0);
		}else{
			throw new Exception("参数异常！");
		}
	}
	private static List<String> getScript(String evl) throws Exception{
		char[] evlArray = evl.toCharArray();
		String type = null;
		int start = 0;
		int functionCount = 0;
		int end = 0;
		int maxCount = 0;
		int middleCount = 0;
		int singleSign = 0;
		int doubleSign = 0;
		boolean istrope = false;
		List<int[]> splitList = new ArrayList<int[]>();
		List<String> orders = new ArrayList<String>();
		for (int i = 0; i < evlArray.length; i++) {
			if(type==null){
				if(i==evlArray.length-1){
					end=evlArray.length;
					splitList.add(new int[]{start,end});
				}else{
					if('#'==evlArray[i]&&evlArray[i+1]=='['){
						orders.add("function");
						type = "function";
						start = i;
						functionCount++;
					}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
						orders.add("function");
						type = "$script";
						start = i;
						functionCount++;
					}else{
						type = "string";
						start = i;
					}
				}
			}else if("function".equals(type)){
				if(singleSign==0&&doubleSign==0){
					if(middleCount == 0){
						if(functionCount==0){
							if(i==evlArray.length-1){
								end=i;
								splitList.add(new int[]{start,end});
								start=i;
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								end=i;
								splitList.add(new int[]{start,end});
								if('#'==evlArray[i]&&evlArray[i+1]=='['){
									orders.add("function");
									type = "function";
									start = i;
									functionCount++;
								}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									type = "$script";
									start = i;
									functionCount++;
								}else{
									type = "string";
									start = i;
								}
							}
						}else{
							if(i==evlArray.length-1){
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								if('#'==evlArray[i]&&evlArray[i+1]=='['){
									orders.add("function");
									functionCount++;
								}else if(']'==evlArray[i]){
									int size = orders.size();
									if(size>0){
										if("sign".equals(orders.get(size-1))){
											middleCount--;
										}else if("function".equals(orders.get(size-1))){
											functionCount--;
										}
										orders.remove(size-1);
									}else{
										throw new Exception("表达式有误"+evl);
									}
								}else if(evlArray[i-1]!='#'&&'['==evlArray[i]){
									orders.add("sign");
									middleCount++;
								}else if(evlArray[i]=='\''&&!istrope){
									singleSign++;
								}else if(evlArray[i]=='\"'&&!istrope){
									doubleSign++;
								}else if(evlArray[i]=='\\'&&!istrope){
									istrope=true;
								}else if(istrope){
									istrope=false;
								}
							}
						}
					}else{
						if(']'==evlArray[i]){
							int size = orders.size();
							if(size>0){
								if("sign".equals(orders.get(size-1))){
									middleCount--;
								}else if("function".equals(orders.get(size-1))){
									functionCount--;
								}
								orders.remove(size-1);
							}else{
								throw new Exception("表达式有误"+evl);
							}
						}else if('#'==evlArray[i]&&evlArray[i+1]=='['){
							orders.add("function");
							functionCount++;
						}else if(evlArray[i-1]!='#'&&'['==evlArray[i]){
							orders.add("sign");
							middleCount++;
						}else if(evlArray[i]=='\''&&!istrope){
							singleSign++;
						}else if(evlArray[i]=='\"'&&!istrope){
							doubleSign++;
						}else if(evlArray[i]=='\\'&&!istrope){
							istrope=true;
						}else if(istrope){
							istrope=false;
						}
					}
				}else if(singleSign!=0&&doubleSign==0){
					if(evlArray[i]=='\''&&!istrope){
						singleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}else if(singleSign==0&&doubleSign!=0){
					if(evlArray[i]=='\"'&&!istrope){
						doubleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}
			}else if("$script".equals(type)){
				if(singleSign==0&&doubleSign==0){
					if(maxCount == 0){
						if(functionCount==0){
							if(i==evlArray.length-1){
								end=i;
								splitList.add(new int[]{start,end});
								start=i;
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								end=i;
								splitList.add(new int[]{start,end});
								if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									type = "$script";
									start = i;
									functionCount++;
								}else if('#'==evlArray[i]&&evlArray[i+1]=='['){
									orders.add("function");
									type = "function";
									start = i;
									functionCount++;
								}else{
									type = "string";
									start = i;
								}
							}
						}else{
							if(i==evlArray.length-1){
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									functionCount++;
								}else if('}'==evlArray[i]){
									int size = orders.size();
									if(size>0){
										if("sign".equals(orders.get(size-1))){
											maxCount--;
										}else if("function".equals(orders.get(size-1))){
											functionCount--;
										}
										orders.remove(size-1);
									}else{
										throw new Exception("表达式有误"+evl);
									}
								}else if(evlArray[i-1]!='$'&&'{'==evlArray[i]){
									orders.add("sign");
									maxCount++;
								}else if(evlArray[i]=='\''&&!istrope){
									singleSign++;
								}else if(evlArray[i]=='\"'&&!istrope){
									doubleSign++;
								}else if(evlArray[i]=='\\'&&!istrope){
									istrope=true;
								}else if(istrope){
									istrope=false;
								}
							}
						}
					}else{
						if('}'==evlArray[i]){
							int size = orders.size();
							if(size>0){
								if("sign".equals(orders.get(size-1))){
									maxCount--;
								}else if("function".equals(orders.get(size-1))){
									functionCount--;
								}
								orders.remove(size-1);
							}else{
								throw new Exception("表达式有误"+evl);
							}
						}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
							orders.add("function");
							functionCount++;
						}else if(evlArray[i-1]!='$'&&'{'==evlArray[i]){
							orders.add("sign");
							maxCount++;
						}else if(evlArray[i]=='\''&&!istrope){
							singleSign++;
						}else if(evlArray[i]=='\"'&&!istrope){
							doubleSign++;
						}else if(evlArray[i]=='\\'&&!istrope){
							istrope=true;
						}else if(istrope){
							istrope=false;
						}
					}
				}else if(singleSign!=0&&doubleSign==0){
					if(evlArray[i]=='\''&&!istrope){
						singleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}else if(singleSign==0&&doubleSign!=0){
					if(evlArray[i]=='\"'&&!istrope){
						doubleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}
			}else if("string".equals(type)){
				if(i==evlArray.length-1){
					end=evlArray.length;
					splitList.add(new int[]{start,end});
				}else{
					if('#'==evlArray[i]&&evlArray[i+1]=='['){
						orders.add("function");
						end=i;
						splitList.add(new int[]{start,end});
						type="function";
						functionCount++;
						start=i;
					}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
						orders.add("function");
						end=i;
						splitList.add(new int[]{start,end});
						type="$script";
						functionCount++;
						start=i;
					}
				}
			}
		}
//		System.out.println("splitList==="+splitList.size());
		List<String> args = new ArrayList<String>();
		for (int[] is : splitList) {
//			System.out.println(evl.substring(is[0],is[1]));
			args.add(evl.substring(is[0],is[1]));
		}
		return args;
	}
	private static List<String> get$List(String evl) throws Exception{
		char[] evlArray = evl.toCharArray();
		String type = null;
		int start = 0;
		int functionCount = 0;
		int maxCount = 0;
		int end = 0;
		int singleSign = 0;
		int doubleSign = 0;
		boolean istrope = false;
		List<int[]> splitList = new ArrayList<int[]>();
		List<String> orders = new ArrayList<String>();
		for (int i = 0; i < evlArray.length; i++) {
			if(type==null){
				if(i==evlArray.length-1){
					end=evlArray.length;
					splitList.add(new int[]{start,end});
				}else{
					if('$'==evlArray[i]&&evlArray[i+1]=='{'){
						orders.add("function");
						type = "function";
						start = i;
						functionCount++;
					}else{
						type = "string";
						start = i;
					}
				}
			}else if("function".equals(type)){
				if(singleSign==0&&doubleSign==0){
					if(maxCount == 0){
						if(functionCount==0){
							if(i==evlArray.length-1){
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								end=i;
								splitList.add(new int[]{start,end});
								if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									type = "function";
									start = i;
									functionCount++;
								}else{
									type = "string";
									start = i;
								}
							}
						}else{
							if(i==evlArray.length-1){
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									functionCount++;
								}else if('}'==evlArray[i]){
									int size = orders.size();
									if(size>0){
										if("sign".equals(orders.get(size-1))){
											maxCount--;
										}else if("function".equals(orders.get(size-1))){
											functionCount--;
										}
										orders.remove(size-1);
									}else{
										throw new Exception("表达式有误"+evl);
									}
								}else if(evlArray[i-1]!='$'&&'{'==evlArray[i]){
									orders.add("sign");
									maxCount++;
								}else if(evlArray[i]=='\''&&!istrope){
									singleSign++;
								}else if(evlArray[i]=='\"'&&!istrope){
									doubleSign++;
								}else if(evlArray[i]=='\\'&&!istrope){
									istrope=true;
								}else if(istrope){
									istrope=false;
								}
							}
						}
					}else{
						if('}'==evlArray[i]){
							int size = orders.size();
							if(size>0){
								if("sign".equals(orders.get(size-1))){
									maxCount--;
								}else if("function".equals(orders.get(size-1))){
									functionCount--;
								}
								orders.remove(size-1);
							}else{
								throw new Exception("表达式有误"+evl);
							}
						}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
							orders.add("function");
							functionCount++;
						}else if(evlArray[i-1]!='$'&&'{'==evlArray[i]){
							orders.add("sign");
							maxCount++;
						}else if(evlArray[i]=='\''&&!istrope){
							singleSign++;
						}else if(evlArray[i]=='\"'&&!istrope){
							doubleSign++;
						}else if(evlArray[i]=='\\'&&!istrope){
							istrope=true;
						}else if(istrope){
							istrope=false;
						}
					}
				}else if(singleSign!=0&&doubleSign==0){
					if(evlArray[i]=='\''&&!istrope){
						singleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}else if(singleSign==0&&doubleSign!=0){
					if(evlArray[i]=='\"'&&!istrope){
						doubleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}
			}else if("string".equals(type)){
				if(singleSign==0&&doubleSign==0){
					if(i==evlArray.length-1){
						end=evlArray.length;
						splitList.add(new int[]{start,end});
					}else{
						if('$'==evlArray[i]&&evlArray[i+1]=='{'){
							orders.add("function");
							end=i;
							splitList.add(new int[]{start,end});
							type="function";
							functionCount++;
							start=i;
						}else if(evlArray[i]=='\''&&!istrope){
							singleSign++;
						}else if(evlArray[i]=='\"'&&!istrope){
							doubleSign++;
						}else if(evlArray[i]=='\\'&&!istrope){
							istrope=true;
						}else if(istrope){
							istrope=false;
						}
					}
				}else if(singleSign!=0&&doubleSign==0){
					if(evlArray[i]=='\''&&!istrope){
						singleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}else if(singleSign==0&&doubleSign!=0){
					if(evlArray[i]=='\"'&&!istrope){
						doubleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}
			}
		}
//		System.out.println("splitList==="+splitList.size());
		List<String> args = new ArrayList<String>();
		for (int[] is : splitList) {
//			System.out.println(evl.substring(is[0],is[1]));
			args.add(evl.substring(is[0],is[1]));
		}
		return args;
	}
	private static Object execute$(String evl,Map<String, Object> map) throws Exception {
		if(evl.startsWith("${")&&evl.endsWith("}")){
//			System.out.println(evl);
//			Pattern pattern1 = Pattern.compile("\\$\\{");
//			Pattern pattern2 = Pattern.compile("\\}");
			evl = evl.substring(evl.indexOf("${")+2,evl.lastIndexOf("}"));
//			String[] args = evl.split("\\.");
			List<String> evlList = functionSplit(evl,'.');
//			int count = 0;
//			StringBuffer buffer = new StringBuffer();
//			for (String arg : args) {
//				buffer.append(arg);
//				Matcher matcher = pattern1.matcher(arg);
//				while(matcher.find()){
//					count++;
//				}
//				matcher = pattern2.matcher(arg);
//				while(matcher.find()){
//					count--;
//				}
//				if(count==0){
//					evlList.add(buffer.toString().trim());
//					buffer.setLength(0);
//				}else{
//					buffer.append(".");
//				}
//			}
			Object object = null;
			for (int index= 0; index < evlList.size(); index++) {
				String arg = evlList.get(index).trim();
				if(arg.startsWith("new")&&arg.contains("(")&&arg.contains(")")){
					for (String importclass : imports) {
						if(importclass.endsWith(arg.substring(0,arg.indexOf("(")).replaceAll("new", "").trim())){
							String typeArgs = arg.substring(arg.indexOf("(")+1, arg.lastIndexOf(")")).trim();
							String[] constructorArgs = null;
							if(typeArgs==null||"".equals(typeArgs)){
								constructorArgs = new String[0];
							}else{
//								count = 0;
								List<String> arglist = functionSplit(typeArgs, ',');
//								buffer = new StringBuffer();
//								String[] constructorArgsL = typeArgs.split(",");
//								for (String constructorArg : constructorArgsL) {
//									buffer.append(constructorArg);
//									Matcher matcher = pattern1.matcher(constructorArg);
//									while(matcher.find()){
//										count++;
//									}
//									matcher = pattern2.matcher(constructorArg);
//									while(matcher.find()){
//										count--;
//									}
//									if(count==0){
//										arglist.add(buffer.toString().trim());
//										buffer.setLength(0);
//									}else{
//										buffer.append(",");
//									}
//								}
								constructorArgs = new String[arglist.size()];
								for (int i = 0; i < arglist.size(); i++) {
									constructorArgs[i]=arglist.get(i);
								}
							}
							Constructor<?>[] constructors = Class.forName(importclass).getConstructors();
							Constructor<?> trueConstructor = null;
							Object[] constructorArgObject = new Object[constructorArgs.length];
							Object[] constructorObject = new Object[constructorArgs.length];
							for (int i = 0; i < constructorArgs.length; i++) {
//								if(constructorArgs[i].startsWith("${")){
								constructorObject[i]=execute(constructorArgs[i],map);
//								}
							}
							listconstructor : for (Constructor<?> constructor : constructors) {
								Class<?>[] parameterTypes = constructor.getParameterTypes();
								if(parameterTypes.length==constructorArgs.length){
									boolean isTrueConstructor = true;
									try {
										for (int i = 0; i < constructorArgs.length; i++) {
											if(String.class.equals(constructorObject[i].getClass())){
												String constructorString = (String) constructorObject[i];
												if(String.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = new String(constructorString);
												}else if(Byte.class.isAssignableFrom(parameterTypes[i])||byte.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = Byte.parseByte(constructorString);
												}else if(Short.class.isAssignableFrom(parameterTypes[i])||short.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = Short.parseShort(constructorString);
												}else if(Integer.class.isAssignableFrom(parameterTypes[i])||int.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = Integer.parseInt(constructorString);
												}else if(Character.class.isAssignableFrom(parameterTypes[i])||char.class.isAssignableFrom(parameterTypes[i])){
													char[] chars = constructorString.toCharArray();
													if(chars.length>1){
														throw new Exception("不是char类型！");
													}else{
														constructorArgObject[i] = chars[0];
													}
												}else if(Long.class.isAssignableFrom(parameterTypes[i])||long.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = Long.parseLong(constructorString);
												}else if(Boolean.class.isAssignableFrom(parameterTypes[i])||boolean.class.isAssignableFrom(parameterTypes[i])){
													if("true".equals(constructorString)||"false".equals(constructorString)){
														constructorArgObject[i] = Boolean.parseBoolean(constructorString);
													}else{
														throw new Exception("不是boolean类型！");
													}
												}else if(Float.class.isAssignableFrom(parameterTypes[i])||float.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = Float.parseFloat(constructorString);
												}else if(Double.class.isAssignableFrom(parameterTypes[i])||double.class.isAssignableFrom(parameterTypes[i])){
													constructorArgObject[i] = Double.parseDouble(constructorString);
												}else{
													throw new Exception("不支持的基本类型！");
												}
											}else{
												constructorArgObject[i] = constructorObject[i];
											}
										}
									} catch (Exception e) {
										continue listconstructor;
									}
									for (int i = 0; i < parameterTypes.length; i++) {
										if(byte.class.isAssignableFrom(parameterTypes[i])){
											if(!Byte.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(short.class.isAssignableFrom(parameterTypes[i])){
											if(!Short.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(int.class.isAssignableFrom(parameterTypes[i])){
											if(!Integer.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(char.class.isAssignableFrom(parameterTypes[i])){
											if(!Character.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(long.class.isAssignableFrom(parameterTypes[i])){
											if(!Long.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(boolean.class.isAssignableFrom(parameterTypes[i])){
											if(!Boolean.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(float.class.isAssignableFrom(parameterTypes[i])){
											if(!Float.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else if(double.class.isAssignableFrom(parameterTypes[i])){
											if(!Double.class.isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}else{
											if(!parameterTypes[i].isAssignableFrom(constructorArgObject[i].getClass())){
												isTrueConstructor = false;
											}
										}
									}
									if(isTrueConstructor){
										trueConstructor = constructor;
										break listconstructor;
									}
								}
							}
							object = trueConstructor.newInstance(constructorArgObject);
						}
					}
				}else if(arg.contains("(")&&arg.contains(")")){
					if(index==0){
						if(object == null){
							for (RuleBean ruleBean : ruleBeans) {
								if(arg.substring(0, arg.indexOf("(")).equals(ruleBean.getBeanName())){
									if("method".equals(ruleBean.getBeanType())){
										String methodEvl = ruleBean.getBeanValue().substring(0, ruleBean.getBeanValue().lastIndexOf("}"))+arg.substring(arg.indexOf("("), arg.lastIndexOf(")")+1)+"}";
//										System.out.println("methodEvl==="+methodEvl);
										object = execute(methodEvl,map);
									}else{
										throw new Exception("执行"+arg+"方法时发生空指针异常！");
									}
								}
							}
						}
						if(object == null){
							object = execute(arg,map);
						}
					}else{
						if(object == null){
							throw new Exception("执行"+arg+"方法时发生空指针异常！");
						}
						Method[] methods = null;
						if(object.getClass().equals(Class.class)){
							Class<?> clazz = (Class<?>) object;
							methods = clazz.getMethods();
						}else{
							methods = object.getClass().getMethods();
						}
						String[] methodArgs = null;
						String typeArgs = arg.substring(arg.indexOf("(")+1, arg.lastIndexOf(")")).trim();
						if("".equals(typeArgs.trim())||typeArgs.trim()==null){
							methodArgs = new String[0];
						}else{
//							count = 0;
							List<String> arglist = functionSplit(typeArgs, ',');
//							buffer = new StringBuffer();
//							String[] methodArgsL = typeArgs.split(",");
//							for (String methodArg : methodArgsL) {
//								buffer.append(methodArg);
//								Matcher matcher = pattern1.matcher(methodArg);
//								while(matcher.find()){
//									count++;
//								}
//								matcher = pattern2.matcher(methodArg);
//								while(matcher.find()){
//									count--;
//								}
//								if(count==0){
//									arglist.add(buffer.toString().trim());
//									buffer.setLength(0);
//								}else{
//									buffer.append(",");
//								}
//							}
							methodArgs = new String[arglist.size()];
							for (int i = 0; i < arglist.size(); i++) {
								methodArgs[i]=arglist.get(i);
							}
						}
						Method trueMethod = null;
						Object[] methodArgObject = new Object[methodArgs.length];
						Object[] methodObject = new Object[methodArgs.length];
						for (int i = 0; i < methodArgs.length; i++) {
//							if(methodArgs[i].startsWith("${")){
							methodObject[i]=execute(methodArgs[i],map);
//							}
						}
						listmethod : for (Method method : methods) {
							if(method.getName().equals(arg.substring(0,arg.indexOf("(")))){
								Class<?>[] parameterTypes = method.getParameterTypes();
								if(parameterTypes.length==methodArgs.length){
									boolean isTrueMethod = true;
									try {
										for (int i = 0; i < methodArgs.length; i++) {
											if(String.class.equals(methodObject[i].getClass())){
												String methodArg = (String) methodObject[i];
												if(String.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = new String(methodArg);
												}else if(Byte.class.isAssignableFrom(parameterTypes[i])||byte.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = Byte.parseByte(methodArg);
												}else if(Short.class.isAssignableFrom(parameterTypes[i])||short.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = Short.parseShort(methodArg);
												}else if(Integer.class.isAssignableFrom(parameterTypes[i])||int.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = Integer.parseInt(methodArg);
												}else if(Character.class.isAssignableFrom(parameterTypes[i])||char.class.isAssignableFrom(parameterTypes[i])){
													char[] chars = methodArg.toCharArray();
													if(chars.length>1){
														throw new Exception("不是char类型！");
													}else{
														methodArgObject[i] = chars[0];
													}
												}else if(Long.class.isAssignableFrom(parameterTypes[i])||long.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = Long.parseLong(methodArg);
												}else if(Boolean.class.isAssignableFrom(parameterTypes[i])||boolean.class.isAssignableFrom(parameterTypes[i])){
													if("true".equals(methodArg)||"false".equals(methodArg)){
														methodArgObject[i] = Boolean.parseBoolean(methodArg);
													}else{
														throw new Exception("不是boolean类型！");
													}
												}else if(Float.class.isAssignableFrom(parameterTypes[i])||float.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = Float.parseFloat(methodArg);
												}else if(Double.class.isAssignableFrom(parameterTypes[i])||double.class.isAssignableFrom(parameterTypes[i])){
													methodArgObject[i] = Double.parseDouble(methodArg);
												}else{
													throw new Exception("不支持的基本类型！");
												}
											}else{
												methodArgObject[i] = methodObject[i];
											}
										}
									} catch (Exception e) {
										continue listmethod;
									}
									for (int i = 0; i < parameterTypes.length; i++) {
										if(byte.class.isAssignableFrom(parameterTypes[i])){
											if(!Byte.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(short.class.isAssignableFrom(parameterTypes[i])){
											if(!Short.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(int.class.isAssignableFrom(parameterTypes[i])){
											if(!Integer.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(char.class.isAssignableFrom(parameterTypes[i])){
											if(!Character.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(long.class.isAssignableFrom(parameterTypes[i])){
											if(!Long.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(boolean.class.isAssignableFrom(parameterTypes[i])){
											if(!Boolean.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(float.class.isAssignableFrom(parameterTypes[i])){
											if(!Float.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else if(double.class.isAssignableFrom(parameterTypes[i])){
											if(!Double.class.isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}else{
											if(!parameterTypes[i].isAssignableFrom(methodArgObject[i].getClass())){
												isTrueMethod = false;
											}
										}
									}
									if(isTrueMethod){
										trueMethod = method;
										break listmethod;
									}
								}
							}
						}
						if(trueMethod!=null){
							if(!trueMethod.isAccessible()){
								trueMethod.setAccessible(true);
							}
							try {
								object = trueMethod.invoke(object, methodArgObject);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}else{
							throw new Exception("无匹配方法"+arg);
						}
					}
				}else if("null".equals(arg)){
					if(evlList.size()!=1){
						throw new Exception("空指针异常！"+arg);
					}
					object = null;
				}else{
					if(index==0){
						for (String importclass : imports) {
							if(importclass.endsWith(arg)){
								object = Class.forName(importclass);
							}
						}
						if(object==null&&applicationContext!=null){
							try {
								object = applicationContext.getBean(arg);
							} catch (BeansException e) {
								object = null;
							}
						}
						if(object==null){
							if(map!=null){
								object= map.get(arg);
							}
						}
						boolean notExists = true;
						if(object==null){
							for (RuleBean ruleBean : ruleBeans) {
								if(arg.equals(ruleBean.getBeanName())){
									notExists = false;
									if("function".equals(ruleBean.getBeanType())){
										object = execute(ruleBean.getBeanValue(),map);
									}else{
										object = ruleBean.getValue();
									}
								}
							}
						}
						if(notExists&&object==null){
							object = execute(arg,map);
						}
//						if(object==null){
//							throw new Exception("未找到对象！"+arg);
//						}
					}else{
						if(object==null){
							throw new Exception("调用"+arg+"属性时发生空指针异常！");
						}
						Field field = null;
						if(object.getClass().equals(Class.class)){
							Class<?> clazz = (Class<?>) object;
							field = clazz.getField(arg);
						}else{
							field = object.getClass().getField(arg);
						}
						if(!field.isAccessible()){
							field.setAccessible(true);
						}
						object = field.get(object);
					}
				}
			}
			return object;
		}else{
			throw new Exception("表达式格式有误！"+evl);
		}
	}
	private static List<String> functionSplit(String evl,char c) throws Exception{
		char[] evlArray = evl.toCharArray();
		String type = null;
		int start = 0;
		int end = 0;
		int functionCount = 0;
		int maxCount = 0;
		int middleCount = 0;
		int minCount = 0;
		int singleSign = 0;
		int doubleSign = 0;
		boolean istrope = false;
		List<String> orders = new ArrayList<String>();
		List<int[]> splitList = new ArrayList<int[]>();
		for (int i = 0; i < evlArray.length; i++) {
			if(type==null){
				if(i==evlArray.length-1){
					splitList.add(new int[]{start,evlArray.length});
				}else{
					if('#'==evlArray[i]&&evlArray[i+1]=='['){
						orders.add("function");
						type = "function";
						functionCount++;
					}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
						orders.add("function");
						type = "$script";
						functionCount++;
					}else if(c==evlArray[i]&&minCount==0){
						type = "string";
						end=i;
						splitList.add(new int[]{start,end});
						start=i+1;
					}else if('('==evlArray[i]){
						type = "string";
						minCount++;
					}else if(')'==evlArray[i]){
						type = "string";
						minCount--;
					}else{
						type = "string";
					}
				}
			}else if("function".equals(type)){
				if(singleSign==0&&doubleSign==0){
					if(middleCount == 0){
						if(functionCount==0){
							if(i==evlArray.length-1){
								splitList.add(new int[]{start,evlArray.length});
							}else{
								if('#'==evlArray[i]&&evlArray[i+1]=='['){
									orders.add("function");
									type = "function";
									functionCount++;
								}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									type = "$script";
									functionCount++;
								}else if(c==evlArray[i]&&minCount==0){
									type = "string";
									end=i;
									splitList.add(new int[]{start,end});
									start=i+1;
								}else if('('==evlArray[i]){
									type = "string";
									minCount++;
								}else if(')'==evlArray[i]){
									type = "string";
									minCount--;
								}else{
									type = "string";
								}
							}
						}else{
							if(i==evlArray.length-1){
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								if('#'==evlArray[i]&&evlArray[i+1]=='['){
									orders.add("function");
									functionCount++;
								}else if(']'==evlArray[i]){
									int size = orders.size();
									if(size>0){
										if("sign".equals(orders.get(size-1))){
											middleCount--;
										}else if("function".equals(orders.get(size-1))){
											functionCount--;
										}
										orders.remove(size-1);
									}else{
										throw new Exception("表达式有误"+evl);
									}
								}else if(evlArray[i-1]!='#'&&'['==evlArray[i]){
									orders.add("sign");
									middleCount++;
								}else if(evlArray[i]=='\''&&!istrope){
									singleSign++;
								}else if(evlArray[i]=='\"'&&!istrope){
									doubleSign++;
								}else if(evlArray[i]=='\\'&&!istrope){
									istrope=true;
								}else if(istrope){
									istrope=false;
								}
							}
						}
					}else{
						if(i==evlArray.length-1){
							splitList.add(new int[]{start,evlArray.length});
						}
						if(']'==evlArray[i]){
							int size = orders.size();
							if(size>0){
								if("sign".equals(orders.get(size-1))){
									middleCount--;
								}else if("function".equals(orders.get(size-1))){
									functionCount--;
								}
								orders.remove(size-1);
							}else{
								throw new Exception("表达式有误"+evl);
							}
						}else if('#'==evlArray[i]&&evlArray[i+1]=='['){
							orders.add("function");
							functionCount++;
						}else if(evlArray[i-1]!='#'&&'['==evlArray[i]){
							orders.add("sign");
							middleCount++;
						}else if(evlArray[i]=='\''&&!istrope){
							singleSign++;
						}else if(evlArray[i]=='\"'&&!istrope){
							doubleSign++;
						}else if(evlArray[i]=='\\'&&!istrope){
							istrope=true;
						}else if(istrope){
							istrope=false;
						}
					}
				}else if(singleSign!=0&&doubleSign==0){
					if(i==evlArray.length-1){
						splitList.add(new int[]{start,evlArray.length});
					}
					if(evlArray[i]=='\''&&!istrope){
						singleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}else if(singleSign==0&&doubleSign!=0){
					if(i==evlArray.length-1){
						splitList.add(new int[]{start,evlArray.length});
					}
					if(evlArray[i]=='\"'&&!istrope){
						doubleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}
			}else if("$script".equals(type)){
				if(singleSign==0&&doubleSign==0){
					if(maxCount == 0){
						if(functionCount==0){
							if(i==evlArray.length-1){
								splitList.add(new int[]{start,evlArray.length});
							}else{
								if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									type = "$script";
									functionCount++;
								}else if('#'==evlArray[i]&&evlArray[i+1]=='['){
									orders.add("function");
									type = "function";
									functionCount++;
								}else if(c==evlArray[i]&&minCount==0){
									type = "string";
									end=i;
									splitList.add(new int[]{start,end});
									start=i+1;
								}else if('('==evlArray[i]){
									type = "string";
									minCount++;
								}else if(')'==evlArray[i]){
									type = "string";
									minCount--;
								}else{
									type = "string";
								}
							}
						}else{
							if(i==evlArray.length-1){
								end=evlArray.length;
								splitList.add(new int[]{start,end});
							}else{
								if('$'==evlArray[i]&&evlArray[i+1]=='{'){
									orders.add("function");
									functionCount++;
								}else if('}'==evlArray[i]){
									int size = orders.size();
									if(size>0){
										if("sign".equals(orders.get(size-1))){
											maxCount--;
										}else if("function".equals(orders.get(size-1))){
											functionCount--;
										}
										orders.remove(size-1);
									}else{
										throw new Exception("表达式有误"+evl);
									}
								}else if(evlArray[i-1]!='$'&&'{'==evlArray[i]){
									orders.add("sign");
									maxCount++;
								}else if(evlArray[i]=='\''&&!istrope){
									singleSign++;
								}else if(evlArray[i]=='\"'&&!istrope){
									doubleSign++;
								}else if(evlArray[i]=='\\'&&!istrope){
									istrope=true;
								}else if(istrope){
									istrope=false;
								}
							}
						}
					}else{
						if(i==evlArray.length-1){
							splitList.add(new int[]{start,evlArray.length});
						}
						if('}'==evlArray[i]){
							int size = orders.size();
							if(size>0){
								if("sign".equals(orders.get(size-1))){
									maxCount--;
								}else if("function".equals(orders.get(size-1))){
									functionCount--;
								}
								orders.remove(size-1);
							}else{
								throw new Exception("表达式有误"+evl);
							}
						}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
							orders.add("function");
							functionCount++;
						}else if(evlArray[i-1]!='$'&&'{'==evlArray[i]){
							orders.add("sign");
							maxCount++;
						}else if(evlArray[i]=='\''&&!istrope){
							singleSign++;
						}else if(evlArray[i]=='\"'&&!istrope){
							doubleSign++;
						}else if(evlArray[i]=='\\'&&!istrope){
							istrope=true;
						}else if(istrope){
							istrope=false;
						}
					}
				}else if(singleSign!=0&&doubleSign==0){
					if(i==evlArray.length-1){
						splitList.add(new int[]{start,evlArray.length});
					}
					if(evlArray[i]=='\''&&!istrope){
						singleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}else if(singleSign==0&&doubleSign!=0){
					if(i==evlArray.length-1){
						splitList.add(new int[]{start,evlArray.length});
					}
					if(evlArray[i]=='\"'&&!istrope){
						doubleSign--;
					}else if(evlArray[i]=='\\'&&!istrope){
						istrope=true;
					}else if(istrope){
						istrope=false;
					}
				}
			}else if("string".equals(type)){
				if(i==evlArray.length-1){
					splitList.add(new int[]{start,evlArray.length});
				}else{
					if('#'==evlArray[i]&&evlArray[i+1]=='['){
						orders.add("function");
						type="function";
						functionCount++;
					}else if('$'==evlArray[i]&&evlArray[i+1]=='{'){
						orders.add("function");
						type="$script";
						functionCount++;
					}else if(c==evlArray[i]&&minCount==0){
						end=i;
						splitList.add(new int[]{start,end});
						start=i+1;
					}else if('('==evlArray[i]){
						minCount++;
					}else if(')'==evlArray[i]){
						minCount--;
					}
				}
			}
		}
//		System.out.println("splitList==="+splitList.size());
		List<String> args = new ArrayList<String>();
//		int i=0;
		for (int[] index : splitList) {
//			System.out.println("prame"+i+++"===="+evl.substring(index[0], index[1]));
			args.add(evl.substring(index[0], index[1]));
		}
		return args;
	}
	public static void main(String[] args){
		if(args.length==2){
			long start = new Date().getTime();
			try {
				RulesLoad.Load(args[0], args[1]);
			} catch (Exception e) {
				e.printStackTrace();
			}
			long end = new Date().getTime();
			System.out.println("XML脚本执行花费时间："+(end-start));
		}else if(args.length==1){
			long start = new Date().getTime();
			try {
				RulesLoad.Load(System.getProperty("user.dir")+fileSeparator+"rule.xml", args[0]);
			} catch (Exception e) {
				e.printStackTrace();
			}
			long end = new Date().getTime();
			System.out.println("XML脚本执行花费时间："+(end-start));
		}else{
			System.err.println("参数不合法！");
		}
	}
}

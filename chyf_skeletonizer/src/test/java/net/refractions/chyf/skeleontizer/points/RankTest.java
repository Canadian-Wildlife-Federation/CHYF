/*
 * Copyright 2019 Government of Canada
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */
package net.refractions.chyf.skeleontizer.points;

import java.util.HashSet;
import java.util.Set;

import org.geotools.filter.identity.FeatureIdImpl;
import org.geotools.referencing.CRS;
import org.geotools.referencing.ReferencingFactoryFinder;
import org.geotools.util.factory.Hints;
import org.junit.Assert;
import org.junit.Test;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.io.WKTReader;
import org.opengis.filter.identity.FeatureId;
import org.opengis.referencing.crs.CRSAuthorityFactory;
import org.opengis.referencing.crs.CoordinateReferenceSystem;

import net.refractions.chyf.datasource.ChyfDataSource.EfType;
import net.refractions.chyf.datasource.ChyfDataSource.RankType;
import net.refractions.chyf.rank.REdge;
import net.refractions.chyf.rank.RGraph;
import net.refractions.chyf.rank.RankComputer;

/**
 * A few very simple test cases for rank 
 * @author Emily
 *
 */
public class RankTest {

	@Test
	public void testSimple() throws Exception{
		String[] pedges = new String[] {
				"LineString(-1535030.66189604694955051 277271.46739751100540161 699, -1535033.21650219871662557 277255.41937545873224735 699, -1535038.55236695194616914 277243.53527628351002932 699, -1535051.38777456595562398 277222.70529744308441877 698, -1535056.07819737214595079 277209.41944662388414145 698, -1535058.01564230117946863 277192.14925907179713249 698, -1535057.72427001851610839 277175.10408303700387478 698, -1535057.83581924368627369 277157.02549857459962368 698, -1535064.35085180145688355 277139.18000014685094357 698, -1535071.33571398234926164 277122.50490006618201733 698, -1535075.70764654269441962 277113.46962401177734137 697, -1535087.14882124192081392 277096.34488467685878277 697, -1535102.20447422633878887 277086.86264721862971783 697, -1535118.52410711790435016 277080.0038324985653162 696, -1535126.27657861332409084 277069.9736536517739296 696, -1535131.89458860689774156 277057.18080435320734978 695, -1535135.30212123272940516 277050.99529944080859423 695)",
				"LineString(-1535135.30212123272940516 277050.99529944080859423 695, -1535138.28023102856241167 277045.6644869577139616 695, -1535147.97990512242540717 277036.31700907927006483 694, -1535149.93659382406622171 277030.79566950630396605 694, -1535144.22451868373900652 277017.04978382401168346 693, -1535142.81058296887204051 277009.00483136717230082 693, -1535144.88899412914179265 277003.35917386785149574 693, -1535164.31801173905842006 276984.84111297037452459 692, -1535173.39964300463907421 276974.2710201209411025 691, -1535180.544103451538831 276951.4447632934898138 691, -1535188.36268478841520846 276938.24942559190094471 690, -1535192.86589217674918473 276922.88566003181040287 690, -1535201.61203054408542812 276910.1834100428968668 689, -1535207.99771748180501163 276898.66700486373156309 689, -1535220.82553396350704134 276889.41014737915247679 688, -1535235.06728145270608366 276888.19829175155609846 688, -1535250.54470466426573694 276889.4325095396488905 687, -1535252.77354307915084064 276889.20747987739741802 687)"
		};

		String[] sedges = new String[] {
				"LineString(-1535135.30212123272940516 277050.99529944080859423 695, -1535159.79682048410177231 277044.82320859748870134 695, -1535170.42315677809529006 277035.96838240791112185 695, -1535180.27414567186497152 277032.04198143817484379 694, -1535188.87863795016892254 277031.87178914435207844 694, -1535201.72513654828071594 277034.36490132752805948 694, -1535212.43717199331149459 277034.09534379839897156 694, -1535219.58997182222083211 277029.22265916783362627 694, -1535220.55483460775576532 277026.37419674452394247 693, -1535219.08487094729207456 277009.92090222425758839 693, -1535230.84421505103819072 276988.54628715012222528 692, -1535232.92264043423347175 276982.90062032826244831 692, -1535241.10638967738486826 276972.01436332706362009 692, -1535249.28968889336101711 276961.12899119313806295 691, -1535262.21141573344357312 276948.88535344414412975 690, -1535282.10900577553547919 276931.53810899797827005 689, -1535285.45095417532138526 276928.51676639821380377 689, -1535284.8989667221903801 276924.12908100336790085 689, -1535276.89380022627301514 276913.7719001779332757 688, -1535258.61595356254838407 276896.62459145113825798 687, -1535252.77354307915084064 276889.20747987739741802 687)",
		};

		testGraph(pedges, sedges, getLcc());
	}
	
	@Test
	public void testSimple2() throws Exception{
		String[] pedges = new String[] {
				"LineString(-1567333.0900711864233017 285254.33262466173619032 1610, -1567335.3242305014282465 285242.35071850009262562 1603, -1567336.53975792252458632 285230.17220929265022278 1597, -1567336.75824413215741515 285220.66351373214274645 1591, -1567336.76647217688150704 285208.89216201659291983 1585, -1567335.75741049414500594 285196.92377241980284452 1579, -1567335.26120434422045946 285189.17721999064087868 1575, -1567334.79316336265765131 285181.60808735899627209 1569, -1567335.01164937298744917 285172.09937264677137136 1561, -1567335.94482822134159505 285160.82849559094756842 1553, -1567335.95394474151544273 285149.05756967794150114 1546, -1567338.75370027730241418 285135.26072211563587189 1539, -1567340.77804217673838139 285121.01743736304342747 1531, -1567340.80172191699966788 285114.80281831230968237 1528, -1567341.4887058436870575 285112.86234203819185495 1527, -1567342.82652096217498183 285100.55862947553396225 1520, -1567344.29758439515717328 285087.29451371543109417 1512, -1567344.87543500284664333 285080.101148989982903 1510, -1567343.97505089896731079 285073.38647494371980429 1508, -1567347.53881000238470733 285060.87216233275830746 1499, -1567350.67022740165702999 285049.21233652997761965 1489, -1567352.68231680104508996 285040.34748225845396519 1482, -1567353.87462870101444423 285034.38261599652469158 1478, -1567354.22996248048730195 285030.30505283549427986 1472, -1567354.98947516968473792 285025.19557021278887987 1466, -1567360.2341381364967674 285001.67838608846068382 1460, -1567360.71130004012957215 284997.4765727249905467 1454, -1567360.95839130971580744 284988.14655060414224863 1449, -1567362.4048926867544651 284981.09656954277306795 1440, -1567361.65450172638520598 284974.43504652846604586 1440, -1567361.80001182202249765 284968.09614324383437634 1434, -1567360.93734968989156187 284949.78917928505688906 1424, -1567361.22882531769573689 284937.11046704091131687 1416, -1567362.27203727536834776 284931.09284833166748285 1412, -1567364.10247511346824467 284920.14403630793094635 1406, -1567367.81176457554101944 284901.29071179870516062 1396, -1567369.61449525551870465 284890.16356934420764446 1390, -1567371.72863415931351483 284878.30661952961236238 1383, -1567373.17514110682532191 284871.2566030565649271 1379, -1567374.36789402319118381 284865.29303646180778742 1376, -1567377.37750595831312239 284853.7573487339541316 1369, -1567380.50983613054268062 284842.09786439780145884 1361, -1567381.70170216844417155 284836.13383455667644739 1352, -1567388.45765896467491984 284814.16804499179124832 1344, -1567390.95095779281109571 284807.49426361359655857 1338, -1567393.60523912031203508 284800.03614994883537292 1332, -1567393.75209875917062163 284793.69676137249916792 1331, -1567393.76077537308447063 284781.92653645481914282 1323, -1567393.03496156120672822 284769.05069848708808422 1315, -1567393.83310326002538204 284752.34925082791596651 1305, -1567395.35349275171756744 284742.1297190310433507 1299, -1567396.67906910623423755 284735.20435879286378622 1294, -1567398.15463946294039488 284728.33217586670070887 1290, -1567399.88439967739395797 284720.37487717159092426 1289, -1567400.46226465399377048 284713.18139410950243473 1283, -1567403.59461196977645159 284701.52183237951248884 1279, -1567407.83314776094630361 284692.44535176362842321 1275)",
				"LineString(-1567407.83314776094630361 284692.44535176362842321 1275, -1567411.93886843114160001 284684.33062710613012314 1271, -1567415.20716903009451926 284678.10189058259129524 1269, -1567418.47590397577732801 284671.87449803948402405 1267, -1567423.83333584619686007 284660.00386477820575237 1260, -1567428.54994225967675447 284646.72593890596181154 1252, -1567433.2938283474650234 284633.62497555091977119 1244, -1567437.3964331850875169 284619.11760760098695755 1237, -1567439.70496769552119076 284603.96720111463218927 1231, -1567440.13731820438988507 284603.11268759705126286 1231, -1567440.56654194323346019 284595.86554888542741537 1228, -1567441.47294048918411136 284584.4165150485932827 1224, -1567443.30430329707451165 284573.46798046678304672 1218, -1567442.8367070802487433 284565.89999107364565134 1218, -1567443.38776045176200569 284558.5285934591665864 1211, -1567444.53169327019713819 284549.51922945026308298 1208, -1567444.54172795196063817 284537.7484400225803256 1201, -1567442.81840363191440701 284527.54326417203992605 1201, -1567438.4050907080527395 284516.37243302632123232 1196, -1567433.84178126393817365 284505.14841879904270172 1192, -1567431.14866441977210343 284497.79011731315404177 1190, -1567427.76809286116622388 284492.37323955819010735 1188, -1567426.94471435039304197 284488.88154355622828007 1188)",
		};

		String[] sedges = new String[] {
				"LineString(-1567407.83314776094630361 284692.44535176362842321 1275, -1567392.13426712853834033 284674.03114492818713188 1268, -1567386.34653803310357034 284666.74007437191903591 1265, -1567384.9539708832744509 284658.67161752283573151 1262, -1567386.10745123424567282 284637.89246008358895779 1252, -1567388.34343565488234162 284625.91112888604402542 1247, -1567390.8617766285315156 284613.02209863811731339 1241, -1567393.59950951486825943 284590.6245608301833272 1231, -1567394.38406656798906624 284579.29977859556674957 1225, -1567394.86212531477212906 284575.09834154322743416 1224, -1567394.60345723177306354 284569.79127033147960901 1221, -1567395.22837660158984363 284559.25038040615618229 1216, -1567396.29842395219020545 284553.41052159667015076 1212, -1567398.86561423214152455 284543.56715724524110556 1210, -1567402.56402978114783764 284530.09167765639722347 1206, -1567408.20471924240700901 284517.31372288148850203 1200, -1567410.01149718184024096 284512.57948819920420647 1198, -1567411.26409592898562551 284508.82401727419346571 1197, -1567420.35835139360278845 284498.2929617203772068 1192, -1567426.94471435039304197 284488.88154355622828007 1188)",
		};
		
		testGraph(pedges, sedges, getLcc());

		
	}
	
	@Test
	public void testSimple3() throws Exception{
		String[] pedges = new String[] {
				"LineString(-1545219.65855495911091566 273698.23375965747982264 537, -1545231.6957986238412559 273667.5568527365103364 536, -1545251.67489796667359769 273597.20283429976552725 536, -1545277.95429982990026474 273511.94452437944710255 536, -1545298.0852454686537385 273453.39588392805308104 536, -1545330.07996422401629388 273352.18724566418677568 536, -1545338.78001281968317926 273319.15711554884910583 536, -1545334.98739756504073739 273291.2915691202506423 536, -1545332.14356084004975855 273278.53512739297002554 536, -1545325.08248575613833964 273256.9028284577652812 536, -1545315.02514549368061125 273234.21517495438456535 536, -1545309.19429704733192921 273226.78491012938320637 536, -1545306.75354752433486283 273224.74902379978448153 536, -1545291.48741653678007424 273213.99077250529080629 536, -1545279.06703108036890626 273204.23788737319409847 536, -1545269.4934374107979238 273183.7343246191740036 536, -1545268.87735348055139184 273182.50996370334178209 536, -1545269.84707021457143128 273179.66167532652616501 536, -1545271.50325635494664311 273174.87352520599961281 536, -1545274.85269719501957297 273171.85710301715880632 536, -1545287.76987275993451476 273159.45464041270315647 536, -1545321.69438770203851163 273142.03798014018684626 536, -1545338.14427592372521758 273128.70010672416538 536, -1545356.56099722255021334 273109.8426094613969326 536, -1545367.39796619070693851 273109.46741990745067596 536, -1545400.36419184203259647 273114.88406604994088411 536, -1545424.6201389676425606 273109.84024005383253098 536, -1545457.94942519557662308 273105.81374442484229803 536, -1545482.601020592963323 273105.10767843201756477 536, -1545495.87829997111111879 273106.76930881291627884 536, -1545498.57359030237421393 273107.7202342739328742 536, -1545519.05558551196008921 273112.93067878670990467 536, -1545546.77144097071141005 273121.8678409717977047 536, -1545580.85546144796535373 273136.24284255970269442 536, -1545590.93329654773697257 273140.9740306967869401 536, -1545600.91246122866868973 273148.69202276691794395 536, -1545632.90693529532290995 273168.71055823750793934 536, -1545673.11215423862449825 273184.23925636429339647 536, -1545701.98093308135867119 273192.4092623945325613 536, -1545704.82625140086747706 273193.41330617014318705 536, -1545759.48993723024614155 273210.0139683373272419 536, -1545819.88820579648017883 273225.27979228086769581 536, -1545821.6846131319180131 273225.91390137560665607 536, -1545845.08320188825018704 273228.96476307511329651 536, -1545870.71103873266838491 273231.79531462956219912 536, -1545915.26817428064532578 273243.48836731817573309 536, -1545952.50919663510285318 273246.38685492798686028 536, -1545983.11245170794427395 273252.98890468291938305 536, -1545996.60029303492046893 273256.90871630702167749 536, -1546031.22920008306391537 273263.92459909897297621 536, -1546048.53301518876105547 273266.00065018143504858 536, -1546055.8090256352443248 273266.38575817085802555 536, -1546063.04407063242979348 273258.36088779289275408 536, -1546067.00095981801860034 273250.18637930694967508 536, -1546073.61305824643932283 273234.55329359415918589 536, -1546080.77934883255511522 273217.94008183293044567 536, -1546096.23799832235090435 273162.94699080474674702 536, -1546107.17200734559446573 273129.6969359340146184 536, -1546116.01385546638630331 273113.8425463829189539 536, -1546124.83092586765997112 273086.05895963497459888 536, -1546136.33705232269130647 273057.37621561344712973 536, -1546153.78940642974339426 272999.72733615431934595 536, -1546164.1766446519177407 272973.84015288017690182 536, -1546190.04545458103530109 272907.57619231846183538 536, -1546202.73776203906163573 272866.55106823332607746 536, -1546229.72234058054164052 272791.10889884922653437 536, -1546238.85884314845316112 272768.97828091774135828 536)",
				"LineString(-1546238.85884314845316112 272768.97828091774135828 536, -1546245.26141783222556114 272751.08718069456517696 536, -1546261.33790823863819242 272709.07280891202390194 536, -1546285.08535438007675111 272628.28989603836089373 536, -1546296.41788168484345078 272599.37821092084050179 536, -1546339.16927324584685266 272480.80381449963897467 536, -1546342.72436223644763231 272473.66225860547274351 536, -1546355.05992666073143482 272430.32683993596583605 536, -1546367.80101019330322742 272385.95986658055335283 536, -1546380.537690190365538 272353.34449554793536663 536, -1546400.58327521034516394 272309.53987065143883228 536, -1546410.26050054235383868 272285.41657284367829561 536, -1546439.52212989749386907 272186.42851384915411472 536, -1546448.98885563015937805 272160.04691686294972897 536, -1546465.61492827348411083 272122.4242128562182188 536, -1546487.02685334836132824 272048.37192259635776281 536, -1546503.5874495473690331 272002.16066083032637835 536, -1546516.61074988101609051 271968.63888120744377375 536, -1546534.93055832106620073 271914.65288166329264641 536, -1546547.82023081323131919 271882.09120982605963945 536, -1546560.63349294639192522 271846.31239020358771086 536, -1546579.96961883129552007 271804.27057281415909529 536, -1546585.97187565732747316 271787.41372260358184576 536, -1546601.4482222436927259 271732.42403840646147728 536, -1546619.41625295160338283 271682.5112959360703826 536, -1546640.73528318526223302 271611.44716258533298969 536, -1546650.48821512213908136 271584.15990209672600031 536, -1546659.05986779602244496 271557.46247503906488419 536, -1546670.07188708987087011 271532.80197413172572851 536, -1546676.4801871795207262 271514.91249596234411001 536, -1546683.64824439119547606 271491.91758010722696781 536, -1546686.92108075623400509 271485.68462820537388325 536, -1546687.60899444273672998 271483.74415294360369444 536, -1546687.89183199475519359 271482.83653364609926939 536, -1546690.88228724035434425 271477.51030690688639879 536, -1546692.56875377707183361 271472.90008088294416666 536)"				
		};
		
		String[] sedges = new String[] {
				"LineString(-1546238.85884314845316112 272768.97828091774135828 536, -1546246.13540397281758487 272769.36247864365577698 536, -1546270.02811119379475713 272773.76531055849045515 536, -1546286.1440226889681071 272770.04945253673940897 536, -1546293.57016554730944335 272770.4876558892428875 536, -1546330.3109956867992878 272765.65483544487506151 536, -1546349.88877025642432272 272764.16882980428636074 536, -1546377.1310737885069102 272765.55523511115461588 536, -1546406.00236106012016535 272761.97637616097927094 536, -1546443.35974344285205007 272758.36937317904084921 536, -1546495.18580740434117615 272750.46732794679701328 536, -1546515.12376987305469811 272751.29123180452734232 536)",
		};
		
		testGraph(pedges, sedges, getLcc());
	}

	@Test
	public void testSimple4() throws Exception{
		String[] pedges = new String[] {
				"LINESTRING (-73.1494255 45.1224451, -73.1624809 45.1222685, -73.168748 45.1221788, -73.1688878 45.1221792, -73.1689767 45.1221794, -73.1785864 45.1221799)",
				"LINESTRING (-73.1785864 45.1221799, -73.1861241 45.1221828)",
		};

		String[] sedges = new String[] {
				"LINESTRING (-73.1785864 45.1221799, -73.1790536 45.1204714, -73.1791703 45.1200578, -73.1795465 45.1186909, -73.1798973 45.1173511, -73.18004 45.1168206, -73.1804421 45.1153729, -73.180753 45.1143208, -73.1809477 45.1135925, -73.1816007 45.1127034, -73.1816775 45.1126046, -73.1832777 45.1105302)",
		};
		
		testGraph(pedges, sedges, getLatLong());
	}
	
	public CoordinateReferenceSystem getLatLong() throws Exception{
		Hints hints = new Hints(Hints.FORCE_LONGITUDE_FIRST_AXIS_ORDER, Boolean.TRUE);
		CRSAuthorityFactory factory = ReferencingFactoryFinder.getCRSAuthorityFactory("EPSG", hints);
		return factory.createCoordinateReferenceSystem("EPSG:4326");
	}

	public CoordinateReferenceSystem getLcc() throws Exception{
		return CRS.decode("EPSG:3348");
	}
	
	
	private void testGraph(String[] primaryedges, String[] secondaryedges, CoordinateReferenceSystem crs) throws Exception{
		
		WKTReader reader = new WKTReader();

		Set<FeatureId> primaries = new HashSet<>();
		Set<FeatureId> secondaries = new HashSet<>();
		
		int fid = 1;
		RGraph graph = new RGraph();
		for (String e : primaryedges) {
			FeatureId ffid = new FeatureIdImpl(String.valueOf(fid++));
			primaries.add(ffid);
			LineString ls = (LineString) reader.read(e);
			graph.addEdgeTesting(EfType.REACH, ls, ffid);
		}
		
		for (String e : secondaryedges) {
			FeatureId ffid = new FeatureIdImpl(String.valueOf(fid++));
			secondaries.add(ffid);
			LineString ls = (LineString) reader.read(e);
			graph.addEdgeTesting(EfType.REACH, ls, ffid);
		}
		
		RankComputer engine = new RankComputer(crs);
		engine.computeRank(graph);
		
		for (REdge e : graph.getEdges()) {
			if (e.getRank() == RankType.PRIMARY && !primaries.contains(e.getID())) {
				Assert.fail("Incorrect rank applied to edge: " + e.toString());
			}
			if (e.getRank() == RankType.SECONDARY && !secondaries.contains(e.getID())) {
				Assert.fail("Incorrect rank applied to edge: " + e.toString());
			}
		}
	}
}

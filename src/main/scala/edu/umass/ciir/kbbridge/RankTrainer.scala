package edu.umass.ciir.kbbridge

import data.{SimpleEntityMention, EntityMention, TacEntityMention, ScoredWikipediaEntity}
import features.{EntityFeaturesToSvmConverter, FileFeatureLoader}
import serial.EntityMentionProtos.TacEntityMentionLinkerFeatures
import tac.TacQueryUtil
import collection.mutable.ListBuffer
import scala.collection.JavaConversions._
import java.io.{File, PrintWriter}
import ciir.umass.edu.learning.{RANKER_TYPE, RankerFactory, RankList, DataPoint}
import ciir.umass.edu.metric.MetricScorerFactory
import ciir.umass.edu.utilities.MyThreadPool
import scala.util.Random

/**
 * User: jdalton
 * Date: 4/16/13
 */
object RankTrainer extends App {

  val allqueries = TacQueryUtil.selectNonNilEvenOddSplitQueries
  val trainQueries = allqueries._1 ++ allqueries._2

  val featureDataDir = "./data/ltr/svm-e2efeatures"
  (new File(featureDataDir).mkdirs())

  // selectEvenOddSplitQueries()._1 .get("2012").get._2

  val relevantRankList = new ListBuffer[Int]()
  println("All queries: " + trainQueries.size)
  var filteredQueries = trainQueries.filterNot(_.isNilQuery)
  println("Non-Nil queries: " + filteredQueries.size)

  val mentions = FileFeatureLoader.loadProtobufDataForQueries(filteredQueries, args(0))

  // get all the unique features (the domain)
  val features = mentions.map(_.getMention.getCandidatesList.map(_.getRankingFeaturesList.map(_.getKey))).flatten.flatten
  val featureSet = features.toSet
  val featureMap = featureSet.zipWithIndex.toMap
  println("Number of features: " + featureMap.size)

  // serialize the domain file for later use
  val domainMapFile = "./data/ltr/domainMap_e2e"
  val domainWriter = new PrintWriter(domainMapFile)
  for ((k,v) <- featureMap) domainWriter.println(k +"\t" + v)
  domainWriter.close

  val features2SvmConverter = new EntityFeaturesToSvmConverter(domainMapFile)

  val pw = new PrintWriter(new File(featureDataDir + "/train_e2e_no2009"))
  for (mention <- mentions) {
    val mentionFeatures = mention.getMention
    val tacEntityMention = new TacEntityMention(mentionFeatures.getSourceDocId,
      mentionFeatures.getEntityType,
      mentionFeatures.getMentionId,
      mentionFeatures.getEntityName,
      Seq(),
      mention.getNodeId,
      mention.getGroundTruthWikiTitle,
      Seq())
    for (entity <- mentionFeatures.getCandidatesList) {
      // now for the features
      val m2eFeatures = entity.getRankingFeaturesList.map(f => f.getKey -> f.getValue).toMap
      val entityCandidate = new ScoredWikipediaEntity(entity.getWikipediaTitle, entity.getWikipediaId, entity.getScore, entity.getRank, None)
      val svmString = features2SvmConverter.entityToSvmFormat(tacEntityMention, entityCandidate, m2eFeatures)
      pw.println(svmString)
    }

  }
  pw.close()

  //train(mentions)


//  def train(featureData : Seq[TacEntityMentionLinkerFeatures], trainMetric:String = "map") = {
//    MyThreadPool.init(Runtime.getRuntime().availableProcessors())
//
//    val mFact = new MetricScorerFactory()
//    val trainingScorer = mFact.createScorer(trainMetric)
//    val modelDir = "./data/ltr/models"
//    (new File(modelDir).mkdirs())
//    val modelFile = modelDir + "/tac_allqueries_allyears"
//    val rf = new RankerFactory()
//
//    rf.createRanker(RANKER_TYPE.LAMBDAMART).printParameters()
//
//    val mentionFeatures = new ListBuffer[RankList]
//    for (mentionData <- mentions) {
//      val mention = mentionData.getMention
//      val tacEntityMention = new SimpleEntityMention(mention.getSourceDocId, mention.getEntityType, mention.getMentionId, mention.getEntityName, "", groundTruth=mentionData.getGroundTruthWikiTitle)
//      val mentionResults = new RankList()
//      for (entity <- mention.getCandidatesList) {
//        val m2eFeatures = entity.getRankingFeaturesList.map(f => f.getKey -> f.getValue).toMap
//        val entityCandidate = new ScoredWikipediaEntity(entity.getWikipediaTitle, entity.getWikipediaId, entity.getScore, entity.getRank)
//        // this could be better; directly construct a datapoint object instead of serializing the data to a string and then re-parsing it.
//        val svmString = EntityFeaturesToSvmConverter.entityToSvmFormat(tacEntityMention, entityCandidate, m2eFeatures)
//        val featureData = new DataPoint(svmString)
//        mentionResults.add(featureData)
//      }
//      mentionFeatures += mentionResults
//    }
//
//    val train = mentionFeatures
//    val shuffled = Random.shuffle(train.toList)
//    val trainValidationSplit = shuffled.splitAt((shuffled.size * 0.8).toInt)
//
//    val trainSet = trainValidationSplit._1
//    val validationSet = trainValidationSplit._2
//    println("training set size: " + trainSet.size +  "\tvalidation set size:" + validationSet.size)
//
//    // why!?! We need a different way of specifying the feature domain.
//    val features = getFeatureFromSampleVector
//
//    val rFact = new RankerFactory()
//    val ranker = rFact.createRanker(RANKER_TYPE.LAMBDAMART, trainSet, features)
//    ranker.set(trainingScorer)
//    ranker.setValidationSet(validationSet)
//    ranker.init()
//    ranker.learn()
//
//    ranker.save(modelFile);
//    println("Model saved to: " + modelFile)
//  }
//
//  def getFeatureFromSampleVector() : Array[Int] =  {
//    val fc = DataPoint.featureCount
//    val features = Array.ofDim[Int](fc)
//    for(i <- 0 to (fc-1))
//      features(i) = (i+1)
//    return features
//  }
}

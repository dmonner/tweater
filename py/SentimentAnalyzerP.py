#!/usr/bin/python
# -*- coding: utf-8 -*-

from __future__ import with_statement
from edu.umd.cs.dmonner.tweater.util import SentimentAnalyzer
import pickle
import re
#import re2 #@UnresolvedImport
import os
import sys
import time
from time_limit import TimeoutException, time_limit #@UnresolvedImport
import traceback
import nltk
from nltk.corpus import stopwords #@UnresolvedImport

class SentimentAnalyzerP(SentimentAnalyzer, object):
  ''' Sentiment Analyzer Utility '''

  def __init__(self):
    ##### CODE FOR FEATURE EXTRACTION FROM TWEET TEXT
    self.punc_reducer = re.compile(r'(\W)\1+')
    self.repeat_reducer = re.compile(r'(\w)\1{2,}')
    self.punc_breaker_1 = re.compile(r'(\w{2,})(\W\s)')
    self.punc_breaker_2 = re.compile(r'(\s\W)(\w{2,})')
    self.punc_breaker_3 = re.compile(r'(\w{3,})(\W{2}\s)')
    self.punc_breaker_4 = re.compile(r'(\s\W{2})(\w{3,})')
    self.quote_replacer = re.compile(r'&quot;')
    self.amp_replacer = re.compile(r'&amp;')
    self.gt_replacer = re.compile(r'&gt;')
    self.lt_replacer = re.compile(r'&lt;')
    self.mention_replacer = re.compile(r'@\w+')
    self.link_replacer = re.compile(r'(?i)\b((?:https?://|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,4}/)(?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+(?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:\'".,<>?«»“”‘’]))')
    #link_replacer = re2.compile(r'(?i)\b((?:https?://|www\d{0,3}[.]|[a-z0-9.\-]+[.][a-z]{2,4}/)(?:[^\s()<>]+|\(([^\s()<>]+|(\([^\s()<>]+\)))*\))+(?:\(([^\s()<>]+|(\([^\s()<>]+\)))*\)|[^\s`!()\[\]{};:\'".,<>?«»“”‘’]))')
    self.caps_finder = re.compile(r'(\b[A-Z]{4,})\b')
    self.lol_reducer = re.compile(r'\b[aeo]*h[aeo]+(h+[aeo]*)*\b|\bl(o+l+)+s*z*(e?d)?\b|\brofls*z*(e?d)?\b|\blu+l+s*z*(e?d)?\b|\blmf+a+o+\b')
    
    nltk.data.path.append("%s/py/nltk_data" % os.getcwd())
    self.stopwords_dict = [(x, True) for x in stopwords.words()]
    ##### IMPORT THE SENTIMENT CLASSIFIER #####
    try:
      print "Trying to import sentiment classifier; could take a couple minutes..."
      sys.stdout.flush()
      f = open("classifier.pickle", 'r')
      self.classifier = pickle.load(f)
      f.close()
      f = open("features.pickle", 'r')
      self.masterfeats = pickle.load(f)
      f.close()
      print "Sentiment classifier import succeeded!"
      sys.stdout.flush()
    except Exception:
      print "Sentiment classifier import failed!"
      print traceback.format_exc()
      sys.exit(1)

  def featurify(self, text, master = None):
    ext_tokens = []

    # replace "&quot;" with a double quote
    text = self.quote_replacer.sub(r'"', text)
    text = self.amp_replacer.sub(r'&', text)
    text = self.gt_replacer.sub(r'>', text)
    text = self.lt_replacer.sub(r'<', text)

    #print text

    # replace mentions with a dummy string
    (text, num) = self.mention_replacer.subn(r'', text)
    if num > 0:
      ext_tokens.append("<MENTION>")
    # replace links with a dummy string
    (text, num) = self.link_replacer.subn(r'', text)
    if num > 0:
      ext_tokens.append("<LINK>")
    # find words in all caps and add a dummy string to note that
    (text, num) = self.caps_finder.subn(r'\1', text)
    if num > 0:
      ext_tokens.append("<CAPS>")
    # find laughter and replace with a dummy string
    (text, num) = self.lol_reducer.subn(r'', text)
    if num > 0:
      ext_tokens.append("<LULZ>")

    # lowercase everything
    text = text.lower()

    # isolates and reduces long spans of repeated punctuation to a single item (like "...." / " !!!! " / "????")
    text = self.punc_reducer.sub(r' \1 ', text)
    # shorten long spans of repeated word chars to three ("soooooooo" ==> "sooo")
    text = self.repeat_reducer.sub(r'\1\1\1', text)
    # break single-character punctuation off of words of size 2 or more (quotes, exclaims, periods)
    text = self.punc_breaker_1.sub(r' \1 \2 ', text)
    text = self.punc_breaker_2.sub(r' \1 \2 ', text)
    # break double-character punctuation off of words of size 3 or more (quote-period, question-exclaim)
    text = self.punc_breaker_3.sub(r' \1 \2 ', text)
    text = self.punc_breaker_4.sub(r' \1 \2 ', text)

    # split on all whitespace
    tokens = re.split(r'\s+', text)
    # remove stopwords and blanks
    tokens = [x for x in tokens if len(x) > 0 and x not in self.stopwords_dict]
    # add in manual extra tokens
    tokens += ext_tokens

    #print tokens
    #print

    if master == None:
      feats = dict([(word, True) for word in tokens])
    else:
      feats = dict([(word, True) for word in tokens if word in master])

    # make the feature data structure
    return feats

  def process(self, text):
    try:
      # hack to skip statuses that have weird non-unicode text in them;
      # these can cause problems down the line for the regexes in featurify()
      try:
        unicode(text, "ascii", "strict")
      except UnicodeDecodeError:
        #print "Unicode error on status %i; stripping." % row['id']
        #sys.stdout.flush()
        try:
          text = unicode(text, "utf-8").encode("ascii", "ignore")
        except UnicodeDecodeError:
          print "Unicode error on status; skipping."
          sys.stdout.flush()

      # featurify the text, using only the features in the master list
      statfeat = {}
      try:
        with time_limit(10):
          statfeat = self.featurify(text, self.masterfeats)
      except TimeoutException:
        print "Featurify timed out for status_id %i" % row['id']

      if len(statfeat) > 0:
        result = self.classifier.prob_classify(statfeat)
        probs = dict([(x, result.prob(x)) for x in result.samples()])
        # calculate a score in [-1, +1]
        score = probs['pos'] * 2.0 - 1.0
      else:
        # skip classification b/c there are no features!
        score = 0.0

      return score

    except Exception:
      print "Problem processing queries:"
      print traceback.format_exc()
      sys.stdout.flush()




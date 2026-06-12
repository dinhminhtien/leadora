"use client";

import React, { useState } from "react";
import { Star, Send, Sparkles, ShieldCheck, Heart } from "lucide-react";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";

type SubmitFeedbackScreenProps = {
  token: string;
};

export function SubmitFeedbackScreen({ token }: SubmitFeedbackScreenProps) {
  const [rating, setRating] = useState(5);
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [recommendScore, setRecommendScore] = useState(9);
  const [submitted, setSubmitted] = useState(false);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
  };

  if (submitted) {
    return (
      <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
        <Card className="w-full max-w-md border-slate-100 shadow-xl bg-white text-center p-8 space-y-4">
          <div className="size-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto">
            <Heart className="size-8 fill-emerald-600" />
          </div>
          <h2 className="text-xl font-bold text-slate-800">Thank You for Your Feedback!</h2>
          <p className="text-xs text-slate-500 leading-relaxed">
            Your review and NPS scoring has been recorded. This helps us continuously elevate our resort experiences and booking standards.
          </p>
          <span className="text-[10px] text-slate-400 block font-semibold uppercase">Token: {token}</span>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-slate-50 flex items-center justify-center p-4">
      <Card className="w-full max-w-md border-slate-100 shadow-xl bg-white">
        <CardHeader className="text-center pb-2 bg-slate-900 text-white rounded-t-xl py-6">
          <div className="inline-flex items-center gap-1 bg-blue-500/20 text-blue-400 text-[10px] font-bold px-2 py-0.5 rounded-full uppercase tracking-wider mb-2">
            <Sparkles className="size-3" /> Guest Survey
          </div>
          <CardTitle className="text-lg font-bold">Leadora Experience Survey</CardTitle>
          <CardDescription className="text-xs text-slate-400 mt-1">
            We value your honest opinion of our hotel booking and event catering services
          </CardDescription>
        </CardHeader>
        
        <CardContent className="p-6">
          <form onSubmit={handleSubmit} className="space-y-6">
            {/* Star Rating Select */}
            <div className="space-y-2 text-center">
              <label className="text-xs font-bold text-slate-700 block">Overall Satisfaction Rating</label>
              <div className="flex justify-center gap-1.5">
                {[1, 2, 3, 4, 5].map(star => (
                  <button
                    key={star}
                    type="button"
                    onClick={() => setRating(star)}
                    onMouseEnter={() => setHoverRating(star)}
                    onMouseLeave={() => setHoverRating(null)}
                    className="p-1 focus:outline-none transition transform hover:scale-110"
                  >
                    <Star
                      className={`size-8 transition ${
                        star <= (hoverRating ?? rating)
                          ? "text-amber-400 fill-amber-400"
                          : "text-slate-200"
                      }`}
                    />
                  </button>
                ))}
              </div>
              <span className="text-[10px] text-slate-400 block font-bold">
                {rating === 5 ? "Excellent! Cared for every detail" : rating === 4 ? "Good - exceeded expectations" : "Satisfactory - standard service"}
              </span>
            </div>

            {/* Recommendation NPS rating */}
            <div className="space-y-2">
              <div className="flex justify-between text-xs font-semibold text-slate-700">
                <span>Likelihood to Recommend?</span>
                <span className="text-blue-600 font-bold">{recommendScore} / 10</span>
              </div>
              <input
                type="range"
                min="0"
                max="10"
                value={recommendScore}
                onChange={e => setRecommendScore(Number(e.target.value))}
                className="w-full accent-blue-600 h-1.5 bg-slate-100 rounded-lg cursor-pointer"
              />
              <div className="flex justify-between text-[9px] text-slate-400 font-bold uppercase">
                <span>Not Likely</span>
                <span>Very Likely</span>
              </div>
            </div>

            {/* Feedback commentary */}
            <div className="space-y-1.5">
              <label className="text-xs font-bold text-slate-700 block">Tell us more about your stay or booking process</label>
              <textarea
                rows={3}
                required
                placeholder="What did we do well? What can we improve next time?"
                value={comment}
                onChange={e => setComment(e.target.value)}
                className="w-full rounded-lg border border-slate-200 bg-slate-50 p-2.5 text-xs text-slate-800 placeholder-slate-400 focus:outline-none focus:border-blue-500 focus:bg-white transition resize-none"
              />
            </div>

            <Button type="submit" variant="primary" className="w-full py-2 bg-blue-600 hover:bg-blue-700 text-xs font-semibold text-white flex items-center justify-center gap-2">
              <Send className="size-3.5" />
              Submit Review
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}

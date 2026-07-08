"use client";

import React, { useState, useEffect } from "react";
import { Star, Send, Sparkles, AlertCircle, CheckCircle2, User, Loader2 } from "lucide-react";
import { Card } from "@/components/ui/Card";
import { Button } from "@/components/ui/Button";
import { customerFeedbackService, type FeedbackTokenValidation } from "@/services/customer_feedback_service";

type SubmitFeedbackScreenProps = {
  token: string;
};

const CATEGORIES = [
  "Professionalism",
  "Responsiveness",
  "Knowledge",
  "Friendliness",
  "Communication",
  "Problem Solving"
];

export function SubmitFeedbackScreen({ token }: SubmitFeedbackScreenProps) {
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [validationData, setValidationData] = useState<FeedbackTokenValidation | null>(null);

  const [rating, setRating] = useState(5);
  const [hoverRating, setHoverRating] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const [recommendScore, setRecommendScore] = useState(10);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  useEffect(() => {
    async function validate() {
      try {
        const response = await customerFeedbackService.validateToken(token);
        if (response.success && response.data.valid) {
          setValidationData(response.data);
        } else {
          setErrorMsg(response.message || "Survey link is invalid or has expired.");
        }
      } catch (err: any) {
        console.error("Token validation error:", err);
        const msg = err.response?.data?.message || "Could not validate this feedback link.";
        setErrorMsg(msg);
      } finally {
        setLoading(false);
      }
    }
    validate();
  }, [token]);

  const handleCategoryToggle = (cat: string) => {
    if (selectedCategories.includes(cat)) {
      setSelectedCategories(selectedCategories.filter(c => c !== cat));
    } else {
      setSelectedCategories([...selectedCategories, cat]);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!comment.trim()) return;

    setSubmitting(true);
    try {
      let finalComment = comment;
      if (selectedCategories.length > 0) {
        finalComment = `[Tags: ${selectedCategories.join(", ")}] ${comment}`;
      }

      const response = await customerFeedbackService.submitByToken(token, {
        rating,
        comment: finalComment,
        recommendScore,
      });

      if (response.success) {
        setSubmitted(true);
      } else {
        setErrorMsg(response.message || "Submitting feedback failed.");
      }
    } catch (err: any) {
      console.error("Submission error:", err);
      const msg = err.response?.data?.message || "An error occurred during submission.";
      alert(msg);
    } finally {
      setSubmitting(false);
    }
  };

  const getDynamicPrompt = () => {
    const currentRating = hoverRating ?? rating;
    if (currentRating === 5) {
      return "What impressed you the most about the consultant?";
    } else if (currentRating === 4) {
      return "What could the consultant do to make your experience even better?";
    } else if (currentRating === 3) {
      return "How could the consultant improve next time?";
    } else {
      return "We're sorry to hear that. Could you tell us what went wrong with the support?";
    }
  };

  const getRatingLabel = (val: number) => {
    switch (val) {
      case 5: return "Excellent";
      case 4: return "Great";
      case 3: return "Good";
      case 2: return "Fair";
      case 1: return "Poor";
      default: return "Excellent";
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-background bg-dot-pattern flex flex-col items-center justify-center p-4 relative overflow-hidden">
        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-primary/10 dark:bg-primary/20 blur-[120px] pointer-events-none" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[45%] rounded-full bg-accent/10 dark:bg-accent/15 blur-[120px] pointer-events-none" />

        <div className="relative z-10 flex flex-col items-center gap-4">
          <Loader2 className="size-10 animate-spin text-primary mb-2" />
          <p className="text-sm font-semibold text-muted-foreground animate-pulse">Loading survey details...</p>
        </div>
      </div>
    );
  }

  if (errorMsg) {
    return (
      <div className="min-h-screen bg-background bg-dot-pattern flex items-center justify-center p-4 relative overflow-hidden">
        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-primary/10 dark:bg-primary/20 blur-[120px] pointer-events-none" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[45%] rounded-full bg-accent/10 dark:bg-accent/15 blur-[120px] pointer-events-none" />

        <Card className="w-full max-w-md border-border/80 shadow-2xl bg-white/70 dark:bg-zinc-950/70 backdrop-blur-md text-center p-8 space-y-5 rounded-2xl relative z-10">
          <div className="size-16 rounded-2xl bg-danger/10 text-danger flex items-center justify-center mx-auto shadow-inner">
            <AlertCircle className="size-8" />
          </div>
          <div className="space-y-2">
            <h2 className="text-xl font-black text-foreground">Invalid Survey Link</h2>
            <p className="text-xs text-muted-foreground leading-relaxed px-4">
              {errorMsg}
            </p>
          </div>
          <div className="pt-4 border-t border-border/50">
            <span className="text-[10px] text-muted-foreground block font-bold uppercase tracking-widest">
              Leadora Guest Protection
            </span>
          </div>
        </Card>
      </div>
    );
  }

  if (submitted) {
    return (
      <div className="min-h-screen bg-background bg-dot-pattern flex items-center justify-center p-4 relative overflow-hidden">
        <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-primary/10 dark:bg-primary/20 blur-[120px] pointer-events-none" />
        <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[45%] rounded-full bg-accent/10 dark:bg-accent/15 blur-[120px] pointer-events-none" />

        <Card className="w-full max-w-lg border-border/80 shadow-2xl bg-white/80 dark:bg-zinc-950/80 backdrop-blur-md text-center p-8 space-y-6 rounded-2xl relative z-10">
          <div className="size-20 rounded-full bg-success/10 text-success flex items-center justify-center mx-auto shadow-inner">
            <CheckCircle2 className="size-10 animate-bounce-short" />
          </div>
          <div className="space-y-2">
            <h2 className="text-2xl font-black text-foreground tracking-tight">
              Thank you, {validationData?.customerName || "Valued Guest"}!
            </h2>
            <p className="text-sm text-muted-foreground max-w-md mx-auto leading-relaxed">
              Thank you for trusting <span className="font-semibold text-foreground">{validationData?.hotelName || "Leadora"}</span>. Your feedback regarding our sales consultant has been shared with our management team and will help us improve our service quality.
            </p>
          </div>

          <div className="p-5 rounded-2xl bg-slate-50/50 dark:bg-zinc-900/50 border border-border/60 max-w-md mx-auto grid grid-cols-2 gap-4 text-left divide-x divide-border/60">
            <div className="space-y-1.5 pl-2">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider block">Staff Rating</span>
              <div className="flex gap-0.5 text-amber-400">
                {Array.from({ length: rating }).map((_, i) => (
                  <Star key={i} className="size-4 fill-amber-400 animate-pulse" />
                ))}
              </div>
              <span className="text-xs font-black text-foreground block mt-1">{getRatingLabel(rating)}</span>
            </div>

            <div className="space-y-1.5 pl-6">
              <span className="text-[10px] font-bold text-muted-foreground uppercase tracking-wider block">Recommendation</span>
              <div className="flex items-baseline gap-1">
                <span className="text-lg font-black text-primary">{recommendScore}</span>
                <span className="text-xs text-muted-foreground">/ 10</span>
              </div>
              <span className="text-xs font-black text-foreground block mt-1">
                {recommendScore >= 9 ? "Promoter" : recommendScore >= 7 ? "Passive" : "Detractor"}
              </span>
            </div>
          </div>

          <div className="pt-2">
            <span className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary/10 text-primary text-xs font-bold uppercase tracking-wider border border-primary/10">
              Booking Reference: {validationData?.bookingCode}
            </span>
          </div>

          <div className="pt-4 border-t border-border/40 flex justify-center gap-3">
            <a href="/" className="w-full max-w-xs">
              <Button className="w-full py-2.5 bg-primary hover:bg-primary/95 text-white rounded-xl shadow-lg shadow-primary/20 text-xs font-bold flex items-center justify-center gap-2 cursor-pointer transition">
                Return to Website
              </Button>
            </a>
          </div>
        </Card>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background bg-dot-pattern flex items-center justify-center p-4 md:p-8 relative overflow-hidden">
      <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-primary/10 dark:bg-primary/20 blur-[120px] pointer-events-none" />
      <div className="absolute bottom-[-10%] right-[-10%] w-[45%] h-[45%] rounded-full bg-accent/10 dark:bg-accent/15 blur-[120px] pointer-events-none" />

      <Card className="w-full max-w-6xl border-border/80 shadow-2xl bg-white/75 dark:bg-zinc-950/75 backdrop-blur-md rounded-2xl relative z-10 overflow-hidden flex flex-col md:flex-row min-h-[600px]">

        {/* Left Column - Context & Staff */}
        <div className="md:w-4/12 bg-zinc-100/50 dark:bg-zinc-900/50 p-8 md:p-10 border-b md:border-b-0 md:border-r border-border/50 flex flex-col relative">
          <div className="absolute top-0 inset-x-0 h-1.5 bg-linear-to-r from-primary via-indigo-500 to-accent" />

          <div className="flex-1">
            <div className="flex items-center gap-2.5 group mb-8">
              <img
                src="/logo1.jpg"
                alt="Leadora Logo"
                className="size-10 rounded-lg object-left object-cover mix-blend-multiply dark:mix-blend-normal dark:invert shrink-0"
              />
              <div className="flex flex-col text-left">
                <span className="text-sm font-bold text-zinc-800 dark:text-zinc-100 tracking-wider leading-none">Leadora</span>
                <span className="text-[10px] text-zinc-400 dark:text-zinc-500 font-semibold tracking-widest uppercase mt-1">Hotel CRM</span>
              </div>
            </div>

            <div className="inline-flex items-center gap-1.5 bg-primary/10 text-primary dark:bg-primary/20 dark:text-primary-foreground text-[10px] font-bold px-2.5 py-1 rounded-full uppercase tracking-wider mb-4">
              <Sparkles className="size-3" /> Consultant Evaluation
            </div>
            <h2 className="text-3xl font-black tracking-tight text-foreground mb-4">
              Sales Staff Feedback
            </h2>
            <p className="text-sm text-muted-foreground leading-relaxed mb-8">
              Please share your experience working with our sales consultant regarding your booking at <span className="font-semibold text-foreground">{validationData?.hotelName || "Leadora Resort"}</span>. Your feedback helps us improve our booking support.
            </p>
          </div>

          {/* Booking & Staff Context */}
          {validationData && (
            <div className="p-5 bg-white dark:bg-zinc-950 rounded-xl border border-border/60 shadow-sm flex items-center gap-4 relative overflow-hidden mt-auto">
              {validationData.salesStaffAvatar ? (
                <img
                  src={validationData.salesStaffAvatar}
                  alt={validationData.salesStaffName}
                  className="size-16 rounded-full object-cover border-2 border-background shadow-md shrink-0"
                />
              ) : (
                <div className="size-16 rounded-full bg-primary/10 text-primary flex items-center justify-center border-2 border-background shadow-md shrink-0">
                  <User className="size-8" />
                </div>
              )}
              <div className="flex-1 min-w-0">
                <div className="flex flex-col gap-1 mb-1.5">
                  <p className="text-[10px] font-bold text-muted-foreground uppercase tracking-widest">Your Consultant</p>
                  <span className="text-[9px] font-extrabold text-primary bg-primary/10 dark:bg-primary/20 px-1.5 py-0.5 rounded-md uppercase tracking-wider w-fit">
                    Verified
                  </span>
                </div>
                <p className="text-base font-black text-foreground truncate">{validationData.salesStaffName}</p>
                <p className="text-xs text-muted-foreground font-semibold mb-2">Booking Consultant</p>

                <div className="flex flex-wrap gap-x-3 gap-y-1 pt-2 border-t border-border/40 text-[10px] text-muted-foreground">
                  <span>Code: <span className="font-bold text-foreground">{validationData.bookingCode}</span></span>
                  {validationData.checkOutDate && (
                    <span>Check-out: <span className="font-bold text-foreground">{validationData.checkOutDate}</span></span>
                  )}
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Right Column - Form */}
        <div className="md:w-8/12 p-8 md:p-12 flex flex-col justify-center">
          <form onSubmit={handleSubmit} className="space-y-6 w-full">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-8">
              {/* Star Rating */}
              <div className="space-y-3 text-center sm:border-r sm:border-border/50 sm:pr-8">
                <label className="text-sm font-bold text-foreground block">
                  How satisfied were you with the consultant's support?
                </label>
                <div className="flex justify-center gap-1.5">
                  {[1, 2, 3, 4, 5].map(star => (
                    <button
                      key={star}
                      type="button"
                      onClick={() => setRating(star)}
                      onMouseEnter={() => setHoverRating(star)}
                      onMouseLeave={() => setHoverRating(null)}
                      className="p-0.5 focus:outline-none transition transform hover:scale-125 active:scale-90 cursor-pointer"
                    >
                      <Star
                        className={`size-8 transition-all duration-150 ${star <= (hoverRating ?? rating)
                          ? "text-amber-400 fill-amber-400 drop-shadow-[0_2px_8px_rgba(245,158,11,0.25)]"
                          : "text-zinc-200 dark:text-zinc-800"
                          }`}
                      />
                    </button>
                  ))}
                </div>
                <div className="inline-block px-3 py-1 rounded-full bg-amber-500/10 text-amber-600 dark:text-amber-400 text-xs font-extrabold uppercase tracking-wider">
                  {getRatingLabel(hoverRating ?? rating)}
                </div>
              </div>

              {/* NPS */}
              <div className="space-y-3">
                <div className="flex flex-col gap-1 text-sm font-bold text-foreground">
                  <span>How likely are you to recommend this consultant?</span>
                  <span className="text-primary font-black bg-primary/10 px-2 py-0.5 rounded-md w-fit">
                    {recommendScore} / 10
                  </span>
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {[0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10].map(score => {
                    const isSelected = recommendScore === score;
                    return (
                      <button
                        key={score}
                        type="button"
                        onClick={() => setRecommendScore(score)}
                        className={`size-8 rounded-full font-bold text-[11px] flex items-center justify-center border transition-all duration-150 shrink-0 cursor-pointer ${isSelected
                          ? "bg-primary text-primary-foreground border-primary shadow-lg shadow-primary/25 scale-110 font-extrabold"
                          : "bg-zinc-100/50 hover:bg-zinc-100 dark:bg-zinc-900/50 dark:hover:bg-zinc-800 border-border text-muted-foreground hover:text-foreground"
                          }`}
                      >
                        {score}
                      </button>
                    );
                  })}
                </div>
                <div className="flex justify-between text-[10px] text-muted-foreground font-bold uppercase tracking-wider">
                  <span>Not likely</span>
                  <span>Extremely likely</span>
                </div>
              </div>
            </div>

            {/* Categories Section */}
            <div className="flex flex-col sm:flex-row sm:items-start gap-2 sm:gap-4 pt-2 border-t border-border/50">
              <label className="text-sm font-bold text-foreground shrink-0 sm:w-40 sm:pt-1.5">
                What stood out? <span className="font-normal text-muted-foreground text-xs">(Optional)</span>
              </label>
              <div className="flex flex-wrap gap-2">
                {CATEGORIES.map(cat => {
                  const isSelected = selectedCategories.includes(cat);
                  return (
                    <button
                      key={cat}
                      type="button"
                      onClick={() => handleCategoryToggle(cat)}
                      className={`px-3 py-1.5 rounded-full text-xs font-bold transition-all duration-150 border cursor-pointer ${isSelected
                        ? "bg-primary/15 text-primary border-primary/30 dark:bg-primary/25 dark:text-primary-foreground"
                        : "bg-zinc-100/50 border-border text-muted-foreground hover:bg-zinc-100 dark:bg-zinc-900/50 dark:hover:bg-zinc-800"
                        }`}
                    >
                      {cat}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Feedback commentary */}
            <div className="space-y-2">
              <label className="text-sm font-bold text-foreground block">
                {getDynamicPrompt()}
              </label>
              <textarea
                rows={3}
                required
                placeholder="Tell us about your experience...&#10;• Were they responsive?&#10;• Did they answer your questions clearly?&#10;• Was the booking process smooth?"
                value={comment}
                onChange={e => setComment(e.target.value)}
                className="w-full rounded-xl border border-border bg-zinc-100/55 dark:bg-zinc-900/55 p-3.5 text-sm text-foreground placeholder-muted-foreground/60 focus:outline-none focus:border-primary focus:bg-white dark:focus:bg-zinc-950 focus:ring-4 focus:ring-primary/10 transition resize-none leading-relaxed"
              />
            </div>

            <Button
              type="submit"
              disabled={submitting}
              className="w-full py-4 bg-primary hover:bg-primary/95 text-white rounded-xl shadow-lg shadow-primary/20 hover:shadow-xl hover:shadow-primary/25 border border-primary/20 text-sm font-bold flex items-center justify-center gap-2 disabled:opacity-50 transition cursor-pointer"
            >
              {submitting ? (
                <>
                  <Loader2 className="size-4 animate-spin" /> Submitting...
                </>
              ) : (
                <>
                  <Send className="size-4" /> Submit Feedback
                </>
              )}
            </Button>
          </form>
        </div>
      </Card>
    </div>
  );
}
